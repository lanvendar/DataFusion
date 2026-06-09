package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesRuntimeRef;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;
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
    private final WorkerTaskExecutionStateStore stateStore;

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
    public DataxPluginTaskExecutor(DataxParamResolver paramResolver, WorkerTaskExecutionStateStore stateStore,
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
        WorkerTaskExecutionState state = baseState(request, param, submitResult.getStatus())
                .appId(submitResult.getAppId())
                .logPath(submitResult.getLogPath())
                .result(submitResult.getResult())
                .pluginParam(mergedPluginParam(request.getPluginParam(), param, submitResult.getKubernetesRuntimeRef()))
                .build();
        stateStore.record(state);
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(submitResult.getStatus())
                .appId(submitResult.getAppId())
                .logPath(submitResult.getLogPath())
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
        return stateStore.read(request.getTaskInstanceId())
                .orElseGet(() -> WorkerTaskExecutionState.builder()
                        .flowInstanceId(request.getFlowInstanceId())
                        .taskInstanceId(request.getTaskInstanceId())
                        .pluginType(request.getPluginType())
                        .appId(request.getAppId())
                        .status(request.getTaskState())
                        .taskData(request.getTaskData())
                        .pluginParam(request.getPluginParam())
                        .build());
    }

    private DataxRunMode resolveRunMode(TaskRequest request, WorkerTaskExecutionState state) {
        if (state != null && state.getRunMode() != null) {
            return DataxRunMode.parse(state.getRunMode());
        }
        JsonNode pluginParam = state == null ? request.getPluginParam() : state.getPluginParam();
        return DataxRunMode.parse(pluginParam == null ? null : pluginParam.path("runMode").asText(null));
    }

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState state, TaskResult result) {
        WorkerTaskExecutionState next = state == null ? currentState(request) : state;
        next.setStatus(result.getTaskState());
        next.setAppId(result.getAppId() == null ? next.getAppId() : result.getAppId());
        next.setLogPath(result.getLogPath() == null ? next.getLogPath() : result.getLogPath());
        next.setResult(result.getResult());
        stateStore.record(next);
    }

    private WorkerTaskExecutionState.WorkerTaskExecutionStateBuilder baseState(TaskRequest request,
            DataxExecutionParam param, StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .pluginType(PLUGIN_TYPE)
                .runMode(param.getRunMode().name())
                .status(status)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam());
    }

    private JsonNode mergedPluginParam(JsonNode pluginParam, DataxExecutionParam param) {
        DataxKubernetesRuntimeRef runtimeRef = param.getKubernetes() == null ? null : DataxKubernetesRuntimeRef.builder()
                .namespace(param.getKubernetes().getNamespace())
                .jobName(param.getKubernetes().getJobName())
                .secretName(param.getKubernetes().getSecretName())
                .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                .containerName(param.getKubernetes().getContainerName())
                .logStorageUri(param.getKubernetes().getLogStorageUri())
                .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                .deleteJobOnFinish(param.getKubernetes().isDeleteJobOnFinish())
                .build();
        return mergedPluginParam(pluginParam, param, runtimeRef);
    }

    private JsonNode mergedPluginParam(JsonNode pluginParam, DataxExecutionParam param,
            DataxKubernetesRuntimeRef runtimeRef) {
        ObjectNode merged = pluginParam != null && pluginParam.isObject()
                ? pluginParam.deepCopy() : OBJECT_MAPPER.createObjectNode();
        merged.put("runMode", param.getRunMode().name());
        if (runtimeRef != null && param.getRunMode() == DataxRunMode.K8S) {
            merged.set(DataxExecutionParam.RUNTIME_FIELD, OBJECT_MAPPER.valueToTree(runtimeRef));
        }
        return merged;
    }
}
