package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
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
    public TaskRequest prepareTask(TaskRequest request) {
        DataxExecutionParam param = paramResolver.resolve(request);
        request.setPluginParam(mergedPluginParam(request.getPluginParam(), param));
        return request;
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        DataxExecutionParam param = paramResolver.resolve(request);
        DataxTaskRunner runner = runner(param.getRunMode());
        DataxSubmitResult submitResult = runner.submit(request, param);
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
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(submitResult.getStatus())
                .workerResult(workerResult(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                        submitResult.getAppId(), submitResult.getWorkDirPath(), submitResult.getResult()))
                .build();
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskResult result = runner(resolveRunMode(request, state)).stop(request, state);
        recordControlResult(request, state, result);
        return result;
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskResult result = runner(resolveRunMode(request, state)).kill(request, state);
        recordControlResult(request, state, result);
        return result;
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        return true;
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

    private DataxRunMode resolveRunMode(TaskRequest request, WorkerTaskExecutionState state) {
        JsonNode pluginParam = request.getPluginParam();
        if (pluginParam == null && state != null) {
            pluginParam = stateStore.readSnapshot(state.getTaskInstanceId())
                    .map(WorkerTaskExecutionSnap::getPluginParam)
                    .orElse(null);
        }
        return DataxRunMode.parse(pluginParam == null ? null : pluginParam.path("runMode").asText(null));
    }

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState state, TaskResult result) {
        WorkerTaskExecutionState next = state == null ? currentState(request) : state;
        WorkerResult workerResult = result.getWorkerResult();
        WorkerResult requestWorkerResult = request.getWorkerResult();
        next.setStatus(result.getTaskState());
        next.setWorkerId(firstText(next.getWorkerId(), workerResult == null ? null : workerResult.getWorkerId(),
                requestWorkerResult == null ? null : requestWorkerResult.getWorkerId()));
        next.setAppId(workerResult == null || workerResult.getAppId() == null ? next.getAppId() : workerResult.getAppId());
        next.setWorkDirPath(workerResult == null || workerResult.getWorkDirPath() == null ? next.getWorkDirPath()
                : workerResult.getWorkDirPath());
        next.setResult(resultJson(workerResult));
        stateStore.saveState(next);
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

    private JsonNode resultJson(WorkerResult workerResult) {
        if (workerResult == null) {
            return null;
        }
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        if (workerResult.getMessage() != null) {
            result.put("message", workerResult.getMessage());
        }
        if (workerResult.getPluginLogUri() != null) {
            result.put("pluginLogUri", workerResult.getPluginLogUri());
        }
        return result;
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

    private JsonNode mergedPluginParam(JsonNode pluginParam, DataxExecutionParam param) {
        ObjectNode merged = pluginParam != null && pluginParam.isObject()
                ? pluginParam.deepCopy() : OBJECT_MAPPER.createObjectNode();
        merged.put("runMode", param.getRunMode().name());
        return merged;
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
