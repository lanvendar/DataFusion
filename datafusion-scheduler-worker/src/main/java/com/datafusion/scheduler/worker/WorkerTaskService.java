package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.CachedWorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import com.datafusion.scheduler.worker.reporter.NoopTaskResultReporter;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Worker 侧任务操作默认实现.
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
     * 任务结果上报接口.
     */
    private final TaskResultReporter resultReporter;

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
     * @param router 插件路由器
     */
    public WorkerTaskService(WorkerTaskOperatorRouter router) {
        this(router, new CachedWorkerTaskContextStorage(), new NoopTaskResultReporter(), createDefaultAsyncExecutor(),
                SubmitModeEnum.SYNC);
    }

    /**
     * 构造函数.
     *
     * @param router            插件路由器
     * @param contextStore      运行中任务上下文存储
     * @param resultReporter    任务结果上报接口
     * @param asyncExecutor     异步执行器
     * @param defaultSubmitMode 默认提交模式
     */
    public WorkerTaskService(WorkerTaskOperatorRouter router, WorkerTaskContextStorage contextStore,
            TaskResultReporter resultReporter, Executor asyncExecutor, SubmitModeEnum defaultSubmitMode) {
        if (router == null) {
            throw new IllegalArgumentException("router不能为空");
        }
        this.router = router;
        this.contextStore = contextStore == null ? new CachedWorkerTaskContextStorage() : contextStore;
        this.resultReporter = resultReporter == null ? new NoopTaskResultReporter() : resultReporter;
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
        RunningTaskContext existingContext = contextStore.get(request.getTaskInstanceId());
        if (existingContext != null && existingContext.isSubmitted()) {
            return duplicateSubmitResult(existingContext);
        }
        RunningTaskContext context = contextStore.getOrCreate(snapshotRequest(request));
        synchronized (context) {
            if (context.isSubmitted()) {
                return duplicateSubmitResult(context);
            }

            PluginTaskExecutor executor = router.route(request.getPluginType());
            if (executor == null) {
                TaskResult result = failureResult(request, StatusEnum.SUBMIT_FAILURE, "未匹配到插件执行器: " + request.getPluginType());
                updateContext(context, result);
                return result;
            }
            TaskRequest preparedRequest;
            try {
                preparedRequest = prepareRequest(executor, request);
            } catch (RuntimeException e) {
                TaskResult result = failureResult(request, StatusEnum.SUBMIT_FAILURE, e.getMessage());
                updateContext(context, result);
                return result;
            }
            context.updateSnapshot(preparedRequest);

            if (preparedRequest.getSubmitMode() == SubmitModeEnum.ASYNC) {
                TaskResult accepted = baseResult(preparedRequest, StatusEnum.SUBMITTING, null);
                updateContext(context, accepted);
                try {
                    asyncExecutor.execute(() -> {
                        TaskResult result = safeExecuteSubmit(executor, preparedRequest);
                        TaskResult submitResult = normalizeSubmitResult(preparedRequest, result);
                        updateContext(context, submitResult);
                        resultReporter.report(submitResult);
                    });
                } catch (RuntimeException e) {
                    TaskResult result = failureResult(preparedRequest, StatusEnum.SUBMIT_FAILURE, e.getMessage());
                    updateContext(context, result);
                    return result;
                }
                return accepted;
            }

            TaskResult result = safeExecuteSubmit(executor, preparedRequest);
            TaskResult submitResult = normalizeSubmitResult(preparedRequest, result);
            updateContext(context, submitResult);
            return submitResult;
        }
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = contextStore.get(request.getTaskInstanceId());
        if (context != null && isFinalState(context.getTaskState())) {
            return responseFromContext(context);
        }
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType());
        if (executor == null) {
            return failureResult(resolvedRequest, StatusEnum.STOP_FAILURE, "未匹配到插件执行器: " + resolvedRequest.getPluginType());
        }
        TaskResult result = safeExecuteStop(executor, resolvedRequest);
        updateContext(context, result);
        resultReporter.report(result);
        removeFinalContext(context, executor, resolvedRequest, result);
        return result;
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = contextStore.get(request.getTaskInstanceId());
        if (context != null && shouldReturnFinalContextForKill(context.getTaskState())) {
            return responseFromContext(context);
        }
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType());
        if (executor == null) {
            return failureResult(resolvedRequest, StatusEnum.KILLED, "未匹配到插件执行器: " + resolvedRequest.getPluginType());
        }
        TaskResult result = safeExecuteKill(executor, resolvedRequest);
        updateContext(context, result);
        resultReporter.report(result);
        removeFinalContext(context, executor, resolvedRequest, result);
        return result;
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = contextStore.get(request.getTaskInstanceId());
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType());
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

    private TaskResult safeExecuteSubmit(PluginTaskExecutor executor, TaskRequest request) {
        try {
            TaskResult result = executor.submitTask(request);
            return fillResult(request, result, StatusEnum.SUBMIT_SUCCESS);
        } catch (RuntimeException e) {
            return failureResult(request, StatusEnum.SUBMIT_FAILURE, e.getMessage());
        }
    }

    private TaskResult safeExecuteStop(PluginTaskExecutor executor, TaskRequest request) {
        try {
            TaskResult result = executor.stopTask(request);
            return fillResult(request, result, StatusEnum.STOP_FAILURE);
        } catch (RuntimeException e) {
            return failureResult(request, StatusEnum.STOP_FAILURE, e.getMessage());
        }
    }

    private TaskResult safeExecuteKill(PluginTaskExecutor executor, TaskRequest request) {
        try {
            TaskResult result = executor.killTask(request);
            return fillResult(request, result, StatusEnum.UNKNOWN);
        } catch (RuntimeException e) {
            return failureResult(request, StatusEnum.UNKNOWN, e.getMessage());
        }
    }

    private boolean safeExecuteFinish(PluginTaskExecutor executor, TaskRequest request) {
        try {
            return executor.finishTask(request);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private TaskRequest prepareRequest(PluginTaskExecutor executor, TaskRequest request) {
        TaskRequest preparedRequest = executor.prepareTask(request);
        if (preparedRequest == null) {
            throw new IllegalArgumentException("插件准备结果不能为空");
        }
        normalizeRequest(preparedRequest);
        return preparedRequest;
    }

    private TaskRequest snapshotRequest(TaskRequest request) {
        TaskRequest snapshotRequest = new TaskRequest();
        snapshotRequest.setFlowInstanceId(request.getFlowInstanceId());
        snapshotRequest.setTaskInstanceId(request.getTaskInstanceId());
        snapshotRequest.setTaskName(request.getTaskName());
        snapshotRequest.setTaskData(request.getTaskData());
        snapshotRequest.setPluginType(request.getPluginType());
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

    private TaskResult duplicateSubmitResult(RunningTaskContext context) {
        TaskResult result = responseFromContext(context);
        if (result.getWorkerResult() == null) {
            result.setWorkerResult(new WorkerResult());
        }
        result.getWorkerResult().setMessage("重复提交");
        return result;
    }

    private void deleteExecution(String taskInstanceId) {
        if (contextStore instanceof WorkerTaskExecutionStore executionStore) {
            executionStore.deleteExecution(taskInstanceId);
            return;
        }
        contextStore.removeContext(taskInstanceId);
    }

    private void updateContext(RunningTaskContext context, TaskResult result) {
        if (context != null) {
            context.updateResult(result);
            contextStore.save(context);
        }
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

    private boolean shouldReturnFinalContextForKill(StatusEnum state) {
        return isFinalState(state) && state != StatusEnum.UNKNOWN;
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
