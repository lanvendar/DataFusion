package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.CachedWorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
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
        RunningTaskContext context = contextStore.getOrCreate(request);
        synchronized (context) {
            if (context != null && context.isSubmitted()) {
                return responseFromContext(context);
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
            context.updateRequest(preparedRequest);
            contextStore.save(context);

            if (preparedRequest.getSubmitMode() == SubmitModeEnum.ASYNC) {
                TaskResult accepted = baseResult(preparedRequest, StatusEnum.SUBMIT_SUCCESS, "任务已异步提交");
                updateContext(context, accepted);
                asyncExecutor.execute(() -> {
                    TaskResult result = safeExecuteSubmit(executor, preparedRequest);
                    updateContext(context, result);
                    resultReporter.report(result);
                });
                return accepted;
            }

            TaskResult result = safeExecuteSubmit(executor, preparedRequest);
            TaskResult submitResult = normalizeSyncSubmitResult(preparedRequest, result);
            updateContext(context, submitResult);
            if (result != submitResult) {
                updateContext(context, result);
                resultReporter.report(result);
            }
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
        TaskResult result = safeExecuteControl(executor, resolvedRequest, StatusEnum.STOP_FAILURE, TaskControlAction.STOP);
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
        if (context != null && isFinalState(context.getTaskState())) {
            return responseFromContext(context);
        }
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType());
        if (executor == null) {
            return failureResult(resolvedRequest, StatusEnum.KILLED, "未匹配到插件执行器: " + resolvedRequest.getPluginType());
        }
        TaskResult result = safeExecuteControl(executor, resolvedRequest, StatusEnum.KILLED, TaskControlAction.KILL);
        updateContext(context, result);
        resultReporter.report(result);
        removeFinalContext(context, executor, resolvedRequest, result);
        return result;
    }

    @Override
    public TaskResult finishTask(TaskRequest request) {
        normalizeRequest(request);
        validateTaskInstanceId(request);
        RunningTaskContext context = contextStore.get(request.getTaskInstanceId());
        TaskRequest resolvedRequest = context == null ? request : context.fillRequest(request);
        PluginTaskExecutor executor = router.route(resolvedRequest.getPluginType());
        if (executor == null) {
            return failureResult(resolvedRequest, StatusEnum.RUN_FAILURE, "未匹配到插件执行器: " + resolvedRequest.getPluginType());
        }
        TaskResult result = safeExecuteControl(executor, resolvedRequest, StatusEnum.RUN_SUCCESS, TaskControlAction.FINISH);
        updateContext(context, result);
        if (isFinalState(result.getTaskState())) {
            if (context == null) {
                RunningTaskContext finalContext = contextStore.getOrCreate(resolvedRequest);
                updateContext(finalContext, result);
            }
            executor.destroyTask(resolvedRequest);
            contextStore.removeContext(resolvedRequest.getTaskInstanceId());
        }
        return result;
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
            return fillResult(request, result, StatusEnum.RUNNING);
        } catch (RuntimeException e) {
            return failureResult(request, StatusEnum.RUN_FAILURE, e.getMessage());
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

    private TaskResult safeExecuteControl(PluginTaskExecutor executor, TaskRequest request, StatusEnum defaultState,
            TaskControlAction action) {
        try {
            TaskResult result;
            switch (action) {
                case STOP:
                    result = executor.stopTask(request);
                    break;
                case KILL:
                    result = executor.killTask(request);
                    break;
                case FINISH:
                    result = executor.finishTask(request);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的控制动作: " + action);
            }
            return fillResult(request, result, defaultState);
        } catch (RuntimeException e) {
            StatusEnum failureState = action == TaskControlAction.FINISH ? StatusEnum.RUN_FAILURE : defaultState;
            return failureResult(request, failureState, e.getMessage());
        }
    }

    private TaskResult normalizeSyncSubmitResult(TaskRequest request, TaskResult result) {
        if (result == null || result.getTaskState() == null || isFinalState(result.getTaskState())) {
            TaskResult response = baseResult(request, StatusEnum.RUNNING, "任务已同步提交");
            if (result != null && result.getWorkerResult() != null && result.getWorkerResult().getAppId() != null) {
                response.getWorkerResult().setAppId(result.getWorkerResult().getAppId());
            }
            return response;
        }
        if (result.getTaskState() == StatusEnum.SUBMIT_SUCCESS) {
            result.setTaskState(StatusEnum.RUNNING);
        }
        return fillResult(request, result, StatusEnum.RUNNING);
    }

    private TaskResult responseFromContext(RunningTaskContext context) {
        TaskResult result = context.toTaskResult();
        if (result.getTaskState() == null) {
            StatusEnum state = context.getSubmitMode() == SubmitModeEnum.ASYNC ? StatusEnum.SUBMIT_SUCCESS : StatusEnum.RUNNING;
            result.setTaskState(state);
        }
        if (result.getWorkerResult() == null) {
            result.setWorkerResult(WorkerResult.builder().message("重复请求返回当前任务上下文").build());
        } else if (context.getResult() == null && result.getWorkerResult().getMessage() == null) {
            result.getWorkerResult().setMessage("重复请求返回当前任务上下文");
        }
        return result;
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

    /**
     * 任务控制动作.
     */
    private enum TaskControlAction {
        /**
         * 停止任务.
         */
        STOP,

        /**
         * 强制停止任务.
         */
        KILL,

        /**
         * 完成任务.
         */
        FINISH
    }
}
