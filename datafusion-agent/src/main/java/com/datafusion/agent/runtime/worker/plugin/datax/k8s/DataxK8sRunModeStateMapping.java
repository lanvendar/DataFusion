package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DataX K8S run mode state mapping.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Slf4j
@Component
public class DataxK8sRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Kubernetes client.
     */
    private final DataxKubernetesClient kubernetesClient;

    /**
     * Parameter resolver.
     */
    private final DataxParamResolver paramResolver;

    /**
     * Worker task execution store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Constructor.
     *
     * @param kubernetesClient Kubernetes client
     * @param paramResolver    parameter resolver
     * @param stateStore       worker task execution store
     */
    public DataxK8sRunModeStateMapping(DataxKubernetesClient kubernetesClient, DataxParamResolver paramResolver,
            WorkerTaskExecutionStore stateStore) {
        this.kubernetesClient = kubernetesClient;
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
    }

    @Override
    public String pluginType() {
        return DataxPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return DataxRunMode.K8S.name();
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
        DataxExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        return kubernetesClient.queryStatus(runtimeRef(param, state), state.getStatus());
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
        DataxExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        String pluginLogUri = collectLogs(state, param, runtimeRef);
        ObjectNode result = PluginResultJson.build("K8S DataX task finished", pluginType(), runMode(), pluginLogUri, null);
        result.put("finalized", true);
        state.setResult(result);
        stateStore.saveState(state);
    }

    private DataxKubernetesRuntimeRef runtimeRef(DataxExecutionParam param, WorkerTaskExecutionState state) {
        return DataxKubernetesRuntimeRef.builder()
                .namespace(param.getKubernetes().getNamespace())
                .jobName(state.getAppId())
                .secretName(param.getKubernetes().getSecretName())
                .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                .containerName(param.getKubernetes().getContainerName())
                .logStorageUri(param.getKubernetes().getLogStorageUri())
                .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                .deleteJobOnFinish(param.getKubernetes().isDeleteJobOnFinish())
                .build();
    }

    private String collectLogs(WorkerTaskExecutionState state, DataxExecutionParam param,
            DataxKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = kubernetesClient.collectLogs(runtimeRef);
            Path logFile = taskRuntimeDir(state, param).resolve("k8s-datax.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            log.warn("采集K8S DataX日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
            return firstText(pluginLogUri(state), pluginLogUri(runtimeRef));
        }
    }

    private Path taskRuntimeDir(WorkerTaskExecutionState state, DataxExecutionParam param) {
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
        request.setPluginParam(snapshot.getPluginParam());
        return request;
    }

    private String pluginLogUri(DataxKubernetesRuntimeRef runtimeRef) {
        return "k8s://" + runtimeRef.getNamespace() + "/jobs/" + runtimeRef.getJobName();
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
