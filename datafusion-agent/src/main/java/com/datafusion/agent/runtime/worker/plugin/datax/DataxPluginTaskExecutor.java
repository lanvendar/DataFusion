package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * DataX plugin task executor.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class DataxPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * Plugin type.
     */
    public static final String PLUGIN_TYPE = "DATAX";

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Parameter resolver.
     */
    private final DataxParamResolver paramResolver;

    /**
     * State store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Runner map.
     */
    private final Map<DataxRunMode, DataxTaskRunner> runnerMap = new EnumMap<>(DataxRunMode.class);

    /**
     * Constructor.
     *
     * @param paramResolver parameter resolver
     * @param stateStore    state store
     * @param runners       runners
     */
    public DataxPluginTaskExecutor(DataxParamResolver paramResolver, WorkerTaskExecutionStore stateStore,
            List<DataxTaskRunner> runners) {
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
        if (runners != null) {
            runners.forEach(runner -> runnerMap.put(runner.runMode(), runner));
        }
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public void validateTaskRequest(TaskRequest request) {
        paramResolver.resolve(request);
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        DataxExecutionParam param = paramResolver.resolve(request);
        DataxTaskRunner runner = runner(param.getRunMode());
        DataxTaskResult submitResult = runner.submit(param);
        stateStore.saveSnapshot(snapshot(request, param));
        WorkerResult requestWorkerResult = request.getWorkerResult();
        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                .appId(submitResult.getAppId())
                .workDirPath(submitResult.getWorkDirPath())
                .status(submitResult.getStatus())
                .result(submitResult.getResult())
                .build();
        stateStore.saveState(state);
        return taskResult(request, submitResult);
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        DataxExecutionParam param = paramResolver.resolve(resolvedRequest);
        DataxTaskResult result = runner(param.getRunMode()).stop(param, state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        DataxExecutionParam param = paramResolver.resolve(resolvedRequest);
        DataxTaskResult result = runner(param.getRunMode()).kill(param, state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        if (resolvedRequest.getPluginParam() == null) {
            return true;
        }
        DataxExecutionParam param = paramResolver.resolve(resolvedRequest);
        return runner(param.getRunMode()).finish(param, state);
    }

    private DataxTaskRunner runner(DataxRunMode runMode) {
        DataxTaskRunner runner = runnerMap.get(runMode);
        if (runner == null) {
            throw new IllegalArgumentException("未匹配到DataX运行器: " + runMode);
        }
        return runner;
    }

    private WorkerTaskExecutionState currentState(TaskRequest request) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return stateStore.readState(request.getTaskInstanceId())
                .orElseGet(() -> WorkerTaskExecutionState.builder()
                        .taskInstanceId(request.getTaskInstanceId())
                        .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                        .appId(requestWorkerResult == null ? null : requestWorkerResult.getAppId())
                        .status(request.getTaskState())
                        .build());
    }

    private TaskRequest resolveRequest(TaskRequest request, WorkerTaskExecutionState state) {
        if (request.getPluginParam() != null && request.getTaskData() != null) {
            return request;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(request.getTaskInstanceId()).orElse(null);
        if (snapshot == null && state != null) {
            snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        }
        if (snapshot == null) {
            return request;
        }
        TaskRequest resolvedRequest = new TaskRequest();
        resolvedRequest.setFlowInstanceId(firstText(request.getFlowInstanceId(), snapshot.getFlowInstanceId()));
        resolvedRequest.setTaskInstanceId(firstText(request.getTaskInstanceId(), snapshot.getTaskInstanceId()));
        resolvedRequest.setTaskName(firstText(request.getTaskName(), snapshot.getTaskName()));
        resolvedRequest.setPluginType(firstText(request.getPluginType(), snapshot.getPluginType()));
        resolvedRequest.setWorkerResult(request.getWorkerResult());
        resolvedRequest.setTaskState(request.getTaskState());
        resolvedRequest.setSubmitMode(request.getSubmitMode());
        resolvedRequest.setTaskData(request.getTaskData() == null ? snapshot.getTaskData() : request.getTaskData());
        resolvedRequest.setPluginParam(resolvedPluginParam(request, snapshot));
        return resolvedRequest;
    }

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState state, DataxTaskResult result) {
        WorkerTaskExecutionState next = state == null ? currentState(request) : state;
        WorkerResult requestWorkerResult = request.getWorkerResult();
        next.setStatus(result.getStatus());
        next.setWorkerId(firstText(next.getWorkerId(), requestWorkerResult == null ? null : requestWorkerResult.getWorkerId()));
        next.setAppId(result.getAppId() == null ? next.getAppId() : result.getAppId());
        next.setWorkDirPath(result.getWorkDirPath() == null ? next.getWorkDirPath() : result.getWorkDirPath());
        next.setResult(result.getResult());
        stateStore.saveState(next);
    }

    private TaskResult taskResult(TaskRequest request, DataxTaskResult result) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(result.getStatus())
                .workerResult(workerResult(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                        result.getAppId(), result.getWorkDirPath(), result.getResult()))
                .build();
    }

    private WorkerResult workerResult(String workerId, String appId, String workDirPath, JsonNode result) {
        return WorkerResult.builder()
                .workerId(workerId)
                .appId(appId)
                .workDirPath(workDirPath)
                .message(resultText(result, "message"))
                .pluginLogUri(resultText(result, "pluginLogUri"))
                .build();
    }

    private String resultText(JsonNode result, String fieldName) {
        if (result == null || !result.hasNonNull(fieldName)) {
            return null;
        }
        return result.get(fieldName).asText();
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request, DataxExecutionParam param) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                .pluginType(PLUGIN_TYPE)
                .runMode(param.getRunMode().name())
                .taskInstanceId(request.getTaskInstanceId())
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private JsonNode resolvedPluginParam(TaskRequest request, WorkerTaskExecutionSnap snapshot) {
        if (request.getPluginParam() != null) {
            return request.getPluginParam();
        }
        ObjectNode pluginParam = snapshot.getPluginParam() != null && snapshot.getPluginParam().isObject()
                ? (ObjectNode) snapshot.getPluginParam().deepCopy() : OBJECT_MAPPER.createObjectNode();
        if (!pluginParam.hasNonNull(DataxParamResolver.FIELD_RUN_MODE) && snapshot.getRunMode() != null) {
            pluginParam.put(DataxParamResolver.FIELD_RUN_MODE, snapshot.getRunMode());
        }
        return pluginParam;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

}
