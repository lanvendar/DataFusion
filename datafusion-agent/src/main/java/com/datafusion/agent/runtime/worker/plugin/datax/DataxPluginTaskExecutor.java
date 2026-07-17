package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DataX 插件任务执行器公共实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public abstract class DataxPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * 插件类型.
     */
    public static final String PLUGIN_TYPE = "DATAX";

    /**
     * 参数解析器.
     */
    private final DataxParamResolver paramResolver;

    /**
     * 状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 构造函数.
     *
     * @param paramResolver 参数解析器
     * @param stateStore 状态存储
     */
    protected DataxPluginTaskExecutor(DataxParamResolver paramResolver, WorkerTaskExecutionStore stateStore) {
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
    }

    @Override
    public final String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public final void validateTaskRequest(TaskRequest request) {
        paramResolver.resolve(request);
    }

    @Override
    public final TaskResult submitTask(TaskRequest request) {
        DataxExecutionParam param = paramResolver.resolve(request);
        WorkerTaskExecutionState state = currentState(request);
        return submit(request, param, state);
    }

    @Override
    public final TaskResult stopTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request);
        DataxTaskResult result = stop(paramResolver.resolve(resolvedRequest), state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public final TaskResult killTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request);
        DataxTaskResult result = kill(paramResolver.resolve(resolvedRequest), state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public final boolean finishTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request);
        return resolvedRequest.getPluginParam() == null
                || finish(paramResolver.resolve(resolvedRequest), state);
    }

    /**
     * 提交当前运行模式任务.
     *
     * <p>子类应使用传入的状态基线记录提交结果，LOCAL 模式需在记录完成后再注册进程监听。
     *
     * @param request 任务请求
     * @param param   执行参数
     * @param state   第三方动作执行前的状态基线
     * @return 任务响应
     */
    protected abstract TaskResult submit(TaskRequest request, DataxExecutionParam param,
            WorkerTaskExecutionState state);

    /**
     * 停止当前运行模式任务.
     *
     * @param param 执行参数
     * @param state 当前状态
     * @return 执行结果
     */
    protected abstract DataxTaskResult stop(DataxExecutionParam param, WorkerTaskExecutionState state);

    /**
     * 强杀当前运行模式任务.
     *
     * @param param 执行参数
     * @param state 当前状态
     * @return 执行结果
     */
    protected abstract DataxTaskResult kill(DataxExecutionParam param, WorkerTaskExecutionState state);

    /**
     * 清理当前运行模式任务.
     *
     * @param param 执行参数
     * @param state 当前状态
     * @return 是否清理完成
     */
    protected boolean finish(DataxExecutionParam param, WorkerTaskExecutionState state) {
        return true;
    }

    /**
     * 获取状态存储.
     *
     * @return 状态存储
     */
    protected final WorkerTaskExecutionStore stateStore() {
        return stateStore;
    }

    /**
     * 记录提交结果并转换为任务响应.
     *
     * @param request 任务请求
     * @param state   第三方动作执行前的状态基线
     * @param result  DataX 执行结果
     * @return 任务响应
     */
    protected final TaskResult recordSubmitResult(TaskRequest request, WorkerTaskExecutionState state,
            DataxTaskResult result) {
        state.setAppId(result.getAppId());
        state.setWorkDirPath(result.getWorkDirPath());
        state.setStatus(result.getStatus());
        state.setResult(result.getResult());
        stateStore.saveState(state, state.getRevision());
        return taskResult(request, result);
    }

    private WorkerTaskExecutionState currentState(TaskRequest request) {
        WorkerResult workerResult = request.getWorkerResult();
        return stateStore.readState(request.getTaskInstanceId())
                .orElseGet(() -> WorkerTaskExecutionState.builder()
                        .taskInstanceId(request.getTaskInstanceId())
                        .workerId(workerResult == null ? null : workerResult.getWorkerId())
                        .appId(workerResult == null ? null : workerResult.getAppId())
                        .status(request.getTaskState())
                        .build());
    }

    private TaskRequest resolveRequest(TaskRequest request) {
        if (request.getPluginParam() != null && request.getTaskData() != null) {
            return request;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(request.getTaskInstanceId()).orElse(null);
        if (snapshot == null) {
            return request;
        }
        TaskRequest resolvedRequest = new TaskRequest();
        resolvedRequest.setFlowInstanceId(firstText(request.getFlowInstanceId(), snapshot.getFlowInstanceId()));
        resolvedRequest.setTaskInstanceId(firstText(request.getTaskInstanceId(), snapshot.getTaskInstanceId()));
        resolvedRequest.setTaskName(firstText(request.getTaskName(), snapshot.getTaskName()));
        resolvedRequest.setPluginType(firstText(request.getPluginType(), snapshot.getPluginType()));
        resolvedRequest.setRunMode(firstText(request.getRunMode(), snapshot.getRunMode()));
        resolvedRequest.setWorkerResult(request.getWorkerResult());
        resolvedRequest.setTaskState(request.getTaskState());
        resolvedRequest.setSubmitMode(request.getSubmitMode());
        resolvedRequest.setTaskData(request.getTaskData() == null ? snapshot.getTaskData() : request.getTaskData());
        resolvedRequest.setPluginParam(request.getPluginParam() == null
                ? snapshot.getPluginParam() : request.getPluginParam());
        return resolvedRequest;
    }

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState state, DataxTaskResult result) {
        WorkerResult workerResult = request.getWorkerResult();
        state.setStatus(result.getStatus());
        state.setWorkerId(firstText(state.getWorkerId(), workerResult == null ? null : workerResult.getWorkerId()));
        state.setAppId(firstText(result.getAppId(), state.getAppId()));
        state.setWorkDirPath(firstText(result.getWorkDirPath(), state.getWorkDirPath()));
        state.setResult(result.getResult());
        stateStore.saveState(state, state.getRevision());
    }

    private TaskResult taskResult(TaskRequest request, DataxTaskResult result) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        JsonNode resultJson = result.getResult();
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(result.getStatus())
                .workerResult(WorkerResult.builder()
                        .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                        .appId(result.getAppId())
                        .workDirPath(result.getWorkDirPath())
                        .message(resultText(resultJson, "message"))
                        .pluginLogUri(resultText(resultJson, "pluginLogUri"))
                        .build())
                .build();
    }

    private String resultText(JsonNode result, String fieldName) {
        return result != null && result.hasNonNull(fieldName) ? result.get(fieldName).asText() : null;
    }

    private String firstText(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }
}
