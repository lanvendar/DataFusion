package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.CachedWorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Worker 侧任务操作默认实现.
 *
 * <p>任务操作分为三个阶段：本类先保存完整提交快照并预留动作中间态，插件再执行第三方动作，
 * 最后读取当前运行态作为响应。文件执行存储使用 revision CAS 竞争，并且不会根据插件返回值二次写状态，
 * 避免覆盖监听器或状态映射器已经提交的更高 revision。
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public class WorkerTaskService implements WorkerTaskOperator {

    /**
     * 插件路由器.
     */
    private final WorkerTaskOperatorRouter router;

    /**
     * 运行中任务上下文存储.
     */
    private final WorkerTaskContextStorage contextStore;

    /**
     * 异步执行器.
     */
    private final Executor asyncExecutor;

    /**
     * 默认提交模式.
     */
    private final SubmitModeEnum defaultSubmitMode;

    /**
     * 构造函数.
     *
     * @param router            插件路由器
     * @param contextStore      运行中任务上下文存储
     * @param asyncExecutor     异步执行器
     * @param defaultSubmitMode 默认提交模式
     */
    public WorkerTaskService(WorkerTaskOperatorRouter router, WorkerTaskContextStorage contextStore,
            Executor asyncExecutor, SubmitModeEnum defaultSubmitMode) {
        if (router == null) {
            throw new IllegalArgumentException("router不能为空");
        }
        this.router = router;
        this.contextStore = contextStore == null ? new CachedWorkerTaskContextStorage() : contextStore;
        this.asyncExecutor = asyncExecutor == null ? Runnable::run : asyncExecutor;
        this.defaultSubmitMode = defaultSubmitMode == null ? SubmitModeEnum.SYNC : defaultSubmitMode;
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        if (isBlank(request.getPluginType())) {
            throw new IllegalArgumentException("pluginType不能为空");
        }
        if (isBlank(request.getRunMode())) {
            throw new IllegalArgumentException("runMode不能为空");
        }
        RunningTaskContext context = contextStore.getOrCreate(snapshotRequest(request));
        PluginTaskExecutor executor = router.route(request.getPluginType(), request.getRunMode());
        if (executor == null) {
            return failureResult(request, StatusEnum.SUBMIT_FAILURE,
                    "未匹配到插件执行器: " + request.getPluginType() + "/" + request.getRunMode());
        }
        try {
            executor.validateTaskRequest(request);
            normalizeRequest(request);
        } catch (RuntimeException e) {
            return failureResult(request, StatusEnum.SUBMIT_FAILURE, e.getMessage());
        }
        // 快照先于动作中间态持久化，保证提交中断后仍能恢复插件路由和控制参数。
        context.updateSnapshot(request);
        if (contextStore instanceof WorkerTaskExecutionStore executionStore) {
            executionStore.saveSnapshot(context.getSnapshot());
        }
        TaskResult rejected = reserveAction(context, StatusEnum.SUBMITTING);
        if (rejected != null) {
            return rejected;
        }

        if (request.getSubmitMode() == SubmitModeEnum.ASYNC) {
            TaskResult accepted = baseResult(request, StatusEnum.SUBMITTING, null);
            // 队列拒绝只能替换本次预留的 SUBMITTING，不能覆盖后续已产生的新 revision。
            long expectedRevision = context.getExecutionState().getRevision();
            try {
                asyncExecutor.execute(() -> executeAsyncSubmit(context, executor, request));
            } catch (RuntimeException e) {
                TaskResult result = failureResult(request, StatusEnum.SUBMIT_FAILURE, e.getMessage());
                saveExecutionFailure(context, result, expectedRevision);
                return latestResult(context, result);
            }
            return accepted;
        }

        TaskResult result = safeExecuteSubmit(context, executor, request);
        TaskResult submitResult = normalizeSubmitResult(request, result);
        return latestResult(context, submitResult);
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = executionContext(request);
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType(), resolvedRequest.getRunMode());
        if (executor == null) {
            return failureResult(resolvedRequest, StatusEnum.STOP_FAILURE, "未匹配到插件执行器: " + resolvedRequest.getPluginType());
        }
        if (context == null) {
            context = contextStore.getOrCreate(snapshotRequest(resolvedRequest));
            context.updateSnapshot(resolvedRequest);
        }
        TaskResult rejected = reserveAction(context, StatusEnum.STOPPING);
        if (rejected != null) {
            return rejected;
        }
        TaskResult result = latestResult(context, safeExecuteStop(context, executor, resolvedRequest));
        removeFinalContext(context, executor, resolvedRequest, result);
        return result;
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = executionContext(request);
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType(), resolvedRequest.getRunMode());
        if (executor == null) {
            return failureResult(resolvedRequest, StatusEnum.KILLED, "未匹配到插件执行器: " + resolvedRequest.getPluginType());
        }
        if (context == null) {
            context = contextStore.getOrCreate(snapshotRequest(resolvedRequest));
            context.updateSnapshot(resolvedRequest);
        }
        TaskResult rejected = reserveAction(context, StatusEnum.KILLING);
        if (rejected != null) {
            return rejected;
        }
        TaskResult result = latestResult(context, safeExecuteKill(context, executor, resolvedRequest));
        removeFinalContext(context, executor, resolvedRequest, result);
        return result;
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = contextStore.get(request.getTaskInstanceId());
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType(), resolvedRequest.getRunMode());
        if (executor == null) {
            deleteExecution(resolvedRequest.getTaskInstanceId());
            return true;
        }
        boolean finished = safeExecuteFinish(executor, resolvedRequest);
        executor.destroyTask(resolvedRequest);
        deleteExecution(resolvedRequest.getTaskInstanceId());
        return finished;
    }

    private void removeFinalContext(RunningTaskContext context, PluginTaskExecutor executor, TaskRequest request,
            TaskResult result) {
        if (context != null && isFinalState(result.getTaskState())) {
            executor.destroyTask(request);
            contextStore.removeContext(request.getTaskInstanceId());
        }
    }

    private TaskResult safeExecuteSubmit(RunningTaskContext context, PluginTaskExecutor executor, TaskRequest request) {
        long expectedRevision = context.getExecutionState().getRevision();
        try {
            TaskResult result = executor.submitTask(request);
            return fillResult(request, result, StatusEnum.SUBMIT_SUCCESS);
        } catch (RuntimeException e) {
            TaskResult result = failureResult(request, StatusEnum.SUBMIT_FAILURE, e.getMessage());
            saveExecutionFailure(context, result, expectedRevision);
            return result;
        }
    }

    private void executeAsyncSubmit(RunningTaskContext context, PluginTaskExecutor executor, TaskRequest request) {
        if (context.getTaskState() != StatusEnum.SUBMITTING) {
            return;
        }
        TaskResult result = safeExecuteSubmit(context, executor, request);
        TaskResult submitResult = normalizeSubmitResult(request, result);
        latestResult(context, submitResult);
    }

    private TaskResult safeExecuteStop(RunningTaskContext context, PluginTaskExecutor executor, TaskRequest request) {
        long expectedRevision = context.getExecutionState().getRevision();
        try {
            TaskResult result = executor.stopTask(request);
            return fillResult(request, result, StatusEnum.STOP_FAILURE);
        } catch (RuntimeException e) {
            TaskResult result = failureResult(request, StatusEnum.STOP_FAILURE, e.getMessage());
            saveExecutionFailure(context, result, expectedRevision);
            return result;
        }
    }

    private TaskResult safeExecuteKill(RunningTaskContext context, PluginTaskExecutor executor, TaskRequest request) {
        long expectedRevision = context.getExecutionState().getRevision();
        try {
            TaskResult result = executor.killTask(request);
            return fillResult(request, result, StatusEnum.UNKNOWN);
        } catch (RuntimeException e) {
            TaskResult result = failureResult(request, StatusEnum.UNKNOWN, e.getMessage());
            saveExecutionFailure(context, result, expectedRevision);
            return result;
        }
    }

    private boolean safeExecuteFinish(PluginTaskExecutor executor, TaskRequest request) {
        try {
            return executor.finishTask(request);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private TaskRequest snapshotRequest(TaskRequest request) {
        TaskRequest snapshotRequest = new TaskRequest();
        snapshotRequest.setFlowInstanceId(request.getFlowInstanceId());
        snapshotRequest.setTaskInstanceId(request.getTaskInstanceId());
        snapshotRequest.setTaskName(request.getTaskName());
        snapshotRequest.setTaskData(request.getTaskData());
        snapshotRequest.setPluginType(request.getPluginType());
        snapshotRequest.setRunMode(request.getRunMode());
        snapshotRequest.setPluginParam(request.getPluginParam());
        snapshotRequest.setSubmitMode(request.getSubmitMode());
        WorkerResult workerResult = request.getWorkerResult();
        if (workerResult != null) {
            WorkerResult snapshotWorkerResult = new WorkerResult();
            snapshotWorkerResult.setWorkerId(workerResult.getWorkerId());
            snapshotRequest.setWorkerResult(snapshotWorkerResult);
        }
        return snapshotRequest;
    }

    private TaskResult normalizeSubmitResult(TaskRequest request, TaskResult result) {
        TaskResult submitResult = fillResult(request, result, StatusEnum.SUBMIT_SUCCESS);
        if (submitResult.getTaskState() != StatusEnum.SUBMIT_FAILURE) {
            submitResult.setTaskState(StatusEnum.SUBMIT_SUCCESS);
        }
        submitResult.setSubmitMode(request.getSubmitMode());
        return submitResult;
    }

    private TaskResult responseFromContext(RunningTaskContext context) {
        TaskResult result = context.toTaskResult();
        if (result.getTaskState() == null) {
            StatusEnum state = context.getSubmitMode() == SubmitModeEnum.ASYNC ? StatusEnum.SUBMITTING : StatusEnum.RUNNING;
            result.setTaskState(state);
        }
        return result;
    }

    private RunningTaskContext executionContext(TaskRequest request) {
        RunningTaskContext context = contextStore.get(request.getTaskInstanceId());
        if (context != null && context.isSubmitted()) {
            return context;
        }
        if (!(contextStore instanceof WorkerTaskExecutionStore executionStore)) {
            return context;
        }
        WorkerTaskExecutionState state = executionStore.readState(request.getTaskInstanceId()).orElse(null);
        if (state == null || state.getStatus() == null) {
            return context;
        }
        WorkerTaskExecutionSnap snapshot = executionStore.readSnapshot(request.getTaskInstanceId()).orElse(null);
        return RunningTaskContext.fromSnapshotAndState(snapshot, state);
    }

    private void deleteExecution(String taskInstanceId) {
        if (contextStore instanceof WorkerTaskExecutionStore executionStore) {
            executionStore.deleteExecution(taskInstanceId);
            return;
        }
        contextStore.removeContext(taskInstanceId);
    }

    /**
     * 返回动作完成后的权威任务状态.
     *
     * <p>插件返回值只描述本次调用结果；调用期间监听器可能已写入更高 revision，
     * 因此文件状态存储场景必须重新读取 {@code .state}，不能用返回值再次落状态。
     *
     * @param context  运行中任务上下文
     * @param fallback 未读取到权威状态时的回退结果
     * @return 当前任务结果
     */
    private TaskResult latestResult(RunningTaskContext context, TaskResult fallback) {
        if (context == null) {
            return fallback;
        }
        if (!(contextStore instanceof WorkerTaskExecutionStore executionStore)) {
            context.updateResult(fallback);
            contextStore.save(context);
            return responseFromContext(context);
        }
        WorkerTaskExecutionState state = executionStore.readState(context.getTaskInstanceId()).orElse(null);
        if (state == null) {
            return fallback;
        }
        context.setExecutionState(state);
        return responseFromContext(context);
    }

    /**
     * 使用动作开始前的 revision 尝试补写插件执行异常状态.
     *
     * <p>CAS 失败表示插件、监听器或其他控制动作已经提交了新状态，此时保留竞争方结果。
     *
     * @param context          运行中任务上下文
     * @param result           异常结果
     * @param expectedRevision 动作开始前的 revision
     */
    private void saveExecutionFailure(RunningTaskContext context, TaskResult result, long expectedRevision) {
        if (contextStore instanceof WorkerTaskExecutionStore executionStore) {
            context.updateResult(result);
            executionStore.saveState(context.getExecutionState(), expectedRevision);
        }
    }

    /**
     * 在执行第三方动作前预留对应的中间态.
     *
     * <p>重复的 STOPPING/KILLING 用于恢复未完成的幂等动作，因此不重复写中间态但仍允许调用插件；
     * 重复的 SUBMITTING 则必须拒绝，避免重复创建第三方资源。文件状态存储使用 revision CAS 完成动作竞争。
     *
     * @param context      运行中任务上下文
     * @param actionStatus 待预留的动作中间态
     * @return 允许执行时返回 {@code null}，否则返回当前任务结果
     */
    private TaskResult reserveAction(RunningTaskContext context, StatusEnum actionStatus) {
        StatusEnum currentStatus = context.getTaskState();
        if (contextStore instanceof WorkerTaskExecutionStore executionStore) {
            WorkerTaskExecutionState current = executionStore.readState(context.getTaskInstanceId()).orElse(null);
            currentStatus = current == null ? null : current.getStatus();
            if (currentStatus == actionStatus
                    && (actionStatus == StatusEnum.STOPPING || actionStatus == StatusEnum.KILLING)) {
                context.setExecutionState(current);
                return null;
            }
            if (!canStartAction(currentStatus, actionStatus)) {
                if (current != null) {
                    context.setExecutionState(current);
                }
                return unavailableActionResult(context, actionStatus);
            }
            WorkerTaskExecutionState next = current == null ? context.getExecutionState() : current;
            next.setStatus(actionStatus);
            if (!executionStore.saveState(next, current == null ? 0L : current.getRevision())) {
                executionStore.readState(context.getTaskInstanceId()).ifPresent(context::setExecutionState);
                return unavailableActionResult(context, actionStatus);
            }
            context.setExecutionState(next);
            return null;
        }
        if (currentStatus == actionStatus
                && (actionStatus == StatusEnum.STOPPING || actionStatus == StatusEnum.KILLING)) {
            return null;
        }
        if (!canStartAction(currentStatus, actionStatus)) {
            return unavailableActionResult(context, actionStatus);
        }
        context.setTaskState(actionStatus);
        contextStore.save(context);
        return null;
    }

    /**
     * 校验当前 Worker 状态是否允许进入动作中间态.
     *
     * <p>这里只裁决动作前置状态；插件结果和监听结果不在 Service 中做二次迁移判断。
     *
     * @param currentStatus 当前状态
     * @param actionStatus  目标动作中间态
     * @return 是否允许执行
     */
    private boolean canStartAction(StatusEnum currentStatus, StatusEnum actionStatus) {
        if (currentStatus == null) {
            return true;
        }
        return switch (actionStatus) {
            case SUBMITTING -> currentStatus == StatusEnum.SUBMIT_FAILURE
                    || currentStatus == StatusEnum.RUN_FAILURE || currentStatus == StatusEnum.STOP_SUCCESS
                    || currentStatus == StatusEnum.STOP_FAILURE || currentStatus == StatusEnum.KILLED;
            case STOPPING -> currentStatus == StatusEnum.SUBMIT_SUCCESS
                    || currentStatus == StatusEnum.SUBMIT_FAILURE || currentStatus == StatusEnum.RUNNING;
            case KILLING -> currentStatus == StatusEnum.STOP_FAILURE || currentStatus == StatusEnum.UNKNOWN;
            default -> false;
        };
    }

    private TaskResult unavailableActionResult(RunningTaskContext context, StatusEnum actionStatus) {
        TaskResult result = responseFromContext(context);
        if (result.getWorkerResult() == null) {
            result.setWorkerResult(new WorkerResult());
        }
        result.getWorkerResult().setMessage("当前状态不允许进入" + actionStatus);
        return result;
    }

    private void validateTaskInstanceId(TaskRequest request) {
        if (request == null || isBlank(request.getTaskInstanceId())) {
            throw new IllegalArgumentException("taskInstanceId不能为空");
        }
    }

    private void normalizeRequest(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        if (request.getSubmitMode() == null) {
            request.setSubmitMode(defaultSubmitMode);
        }
    }

    private TaskResult fillResult(TaskRequest request, TaskResult result, StatusEnum defaultState) {
        if (result == null) {
            return baseResult(request, defaultState, null);
        }
        if (result.getTaskInstanceId() == null) {
            result.setTaskInstanceId(request.getTaskInstanceId());
        }
        if (result.getFlowInstanceId() == null) {
            result.setFlowInstanceId(request.getFlowInstanceId());
        }
        if (result.getTaskName() == null) {
            result.setTaskName(request.getTaskName());
        }
        if (result.getWorkerResult() == null) {
            result.setWorkerResult(new WorkerResult());
        }
        WorkerResult requestWorkerResult = request.getWorkerResult();
        if (result.getWorkerResult().getWorkerId() == null) {
            result.getWorkerResult().setWorkerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId());
        }
        if (result.getTaskState() == null) {
            result.setTaskState(defaultState);
        }
        if (result.getSubmitMode() == null) {
            result.setSubmitMode(request.getSubmitMode());
        }
        return result;
    }

    private TaskResult baseResult(TaskRequest request, StatusEnum state, String message) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(state)
                .submitMode(request.getSubmitMode())
                .workerResult(WorkerResult.builder()
                        .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                        .appId(requestWorkerResult == null ? null : requestWorkerResult.getAppId())
                        .message(message)
                        .build())
                .build();
    }

    private TaskResult failureResult(TaskRequest request, StatusEnum state, String message) {
        return baseResult(request, state, message == null ? "任务操作失败" : message);
    }

    private boolean isFinalState(StatusEnum state) {
        return state != null && state.isFinalState();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ExecutorService createDefaultAsyncExecutor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
        return executor;
    }

}
