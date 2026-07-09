package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * K8S_OPERATOR Flink run mode state mapping.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Slf4j
@Component
public class K8sOperatorRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Operator client.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * Parameter resolver.
     */
    private final FlinkParamResolver paramResolver;

    /**
     * Worker task execution store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Constructor.
     *
     * @param operatorClient Operator client
     * @param paramResolver  parameter resolver
     * @param stateStore     worker task execution store
     */
    public K8sOperatorRunModeStateMapping(K8sOperatorClient operatorClient, FlinkParamResolver paramResolver,
            WorkerTaskExecutionStore stateStore) {
        this.operatorClient = operatorClient;
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
    }

    @Override
    public String pluginType() {
        return FlinkPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return FlinkRunMode.K8S_OPERATOR.name();
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            return StatusEnum.UNKNOWN;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        if (snapshot == null) {
            return StatusEnum.UNKNOWN;
        }
        FlinkExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        return operatorClient.queryStatus(runtimeRef(param, state), state.getStatus());
    }

    @Override
    public void beforeFinalReport(WorkerTaskExecutionState state) {
        if (state == null || state.getStatus() == null || !state.getStatus().isFinalState()) {
            return;
        }
        if (isFinalized(state)) {
            return;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        if (snapshot == null) {
            return;
        }
        FlinkExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        String pluginLogUri = collectLogs(state, param, runtimeRef);
        ObjectNode result = PluginResultJson.build("K8S_OPERATOR Flink task finished", pluginType(), runMode(),
                pluginLogUri, null);
        result.put("flinkWebUiUri", runtimeRef.getFlinkWebUiUri());
        result.put("finalized", true);
        state.setResult(result);
        stateStore.saveState(state);
    }

    private FlinkKubernetesRuntimeRef runtimeRef(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        return FlinkKubernetesRuntimeRef.builder()
                .namespace(param.getKubernetes().getNamespace())
                .deploymentName(state.getAppId())
                .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                .logStorageUri(param.getKubernetes().getLogStorageUri())
                .flinkWebUiUri(param.getKubernetes().getFlinkWebUiUri())
                .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                .deleteDeploymentOnFinish(param.getKubernetes().isDeleteDeploymentOnFinish())
                .build();
    }

    private String collectLogs(WorkerTaskExecutionState state, FlinkExecutionParam param,
            FlinkKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = operatorClient.collectLogs(runtimeRef);
            Path logFile = taskRuntimeDir(state, param).resolve("k8s-flink.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            log.warn("采集K8S_OPERATOR Flink日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
            return firstText(pluginLogUri(state), pluginLogUri(runtimeRef));
        }
    }

    private Path taskRuntimeDir(WorkerTaskExecutionState state, FlinkExecutionParam param) {
        if (!isBlank(state.getWorkDirPath())) {
            return Path.of(state.getWorkDirPath());
        }
        return param.getWorkDir();
    }

    private TaskRequest taskRequest(WorkerTaskExecutionSnap snapshot) {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId(snapshot.getFlowInstanceId());
        request.setTaskInstanceId(snapshot.getTaskInstanceId());
        request.setTaskName(snapshot.getTaskName());
        request.setPluginType(snapshot.getPluginType());
        request.setTaskData(snapshot.getTaskData());
        request.setPluginParam(resolvedPluginParam(snapshot));
        return request;
    }

    private JsonNode resolvedPluginParam(WorkerTaskExecutionSnap snapshot) {
        ObjectNode pluginParam = snapshot.getPluginParam() != null && snapshot.getPluginParam().isObject()
                ? (ObjectNode) snapshot.getPluginParam().deepCopy() : OBJECT_MAPPER.createObjectNode();
        if (!pluginParam.hasNonNull(FlinkParamResolver.FIELD_RUN_MODE) && snapshot.getRunMode() != null) {
            pluginParam.put(FlinkParamResolver.FIELD_RUN_MODE, snapshot.getRunMode());
        }
        return pluginParam;
    }

    private String pluginLogUri(FlinkKubernetesRuntimeRef runtimeRef) {
        return "k8s-operator://" + runtimeRef.getNamespace() + "/flinkdeployments/"
                + runtimeRef.getDeploymentName();
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
    }

    private boolean isFinalized(WorkerTaskExecutionState state) {
        return state.getResult() != null && state.getResult().path("finalized").asBoolean(false);
    }

    private String firstText(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
