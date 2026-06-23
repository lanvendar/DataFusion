package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
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
        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .workerId(request.getWorkerId())
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
                .workerId(request.getWorkerId())
                .taskState(submitResult.getStatus())
                .appId(submitResult.getAppId())
                .workDirPath(submitResult.getWorkDirPath())
                .result(submitResult.getResult())
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
    public TaskResult finishTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskResult result = runner(resolveRunMode(request, state)).finish(request, state);
        if (result.getTaskState() != null && result.getTaskState().isFinalState()) {
            recordControlResult(request, state, result);
        }
        return result;
    }

    private DataxTaskRunner runner(DataxRunMode runMode) {
        DataxTaskRunner runner = runnerMap.get(runMode);
        if (runner == null) {
            throw new IllegalArgumentException("未匹配到DataX运行器: " + runMode);
        }
        return runner;
    }

    private WorkerTaskExecutionState currentState(TaskRequest request) {
        return stateStore.readState(request.getTaskInstanceId())
                .orElseGet(() -> WorkerTaskExecutionState.builder()
                        .taskInstanceId(request.getTaskInstanceId())
                        .workerId(request.getWorkerId())
                        .appId(request.getAppId())
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
        next.setStatus(result.getTaskState());
        next.setWorkerId(firstText(next.getWorkerId(), result.getWorkerId(), request.getWorkerId()));
        next.setAppId(result.getAppId() == null ? next.getAppId() : result.getAppId());
        next.setWorkDirPath(result.getWorkDirPath() == null ? next.getWorkDirPath() : result.getWorkDirPath());
        next.setResult(result.getResult());
        stateStore.saveState(next);
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request, DataxExecutionParam param) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .workerId(request.getWorkerId())
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
