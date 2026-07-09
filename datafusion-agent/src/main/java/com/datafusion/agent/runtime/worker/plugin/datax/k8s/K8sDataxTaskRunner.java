package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxTaskResult;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxTaskRunner;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * K8S DataX task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class K8sDataxTaskRunner implements DataxTaskRunner {

    /**
     * Kubernetes client.
     */
    private final DataxKubernetesClient kubernetesClient;

    /**
     * State store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Constructor.
     *
     * @param kubernetesClient Kubernetes client
     * @param stateStore       state store
     */
    public K8sDataxTaskRunner(DataxKubernetesClient kubernetesClient, WorkerTaskExecutionStore stateStore) {
        this.kubernetesClient = kubernetesClient;
        this.stateStore = stateStore;
    }

    @Override
    public DataxRunMode runMode() {
        return DataxRunMode.K8S;
    }

    @Override
    public DataxTaskResult submit(DataxExecutionParam param) {
        WorkerTaskExecutionState oldState = stateStore.readState(param.getTaskInstanceId()).orElse(null);
        if (oldState != null && !isBlank(oldState.getAppId())) {
            try {
                if (!kubernetesClient.cleanup(runtimeRef(param, oldState), DataxKubernetesCleanupMode.BEFORE_SUBMIT)) {
                    return result(oldState, StatusEnum.SUBMIT_FAILURE, "K8S DataX cleanup before submit unfinished", null);
                }
            } catch (RuntimeException e) {
                return result(oldState, StatusEnum.SUBMIT_FAILURE, "K8S DataX cleanup before submit failed: "
                        + e.getMessage(), null);
            }
        }
        try {
            DataxKubernetesRuntimeRef runtimeRef = kubernetesClient.submit(param);
            return DataxTaskResult.builder()
                    .status(StatusEnum.RUNNING)
                    .appId(runtimeRef.getJobName())
                    .workDirPath(param.getWorkDir().toString())
                    .result(resultJson("K8S DataX job submitted", pluginLogUri(runtimeRef)))
                    .kubernetesRuntimeRef(runtimeRef)
                    .build();
        } catch (Exception e) {
            return DataxTaskResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .result(PluginResultJson.build(e.getMessage(), "DATAX", DataxRunMode.K8S.name(), null, null))
                    .build();
        }
    }

    @Override
    public DataxTaskResult stop(DataxExecutionParam param, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        kubernetesClient.stop(runtimeRef, false);
        return result(state, StatusEnum.STOPPING, "K8S DataX stop requested", null);
    }

    @Override
    public DataxTaskResult kill(DataxExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return result(state, StatusEnum.KILLED, "K8S DataX runtime ref not found, nothing to kill", null);
        }
        try {
            DataxKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            kubernetesClient.stop(runtimeRef, true);
            return result(state, StatusEnum.KILLED, "K8S DataX kill completed", null);
        } catch (RuntimeException e) {
            return result(state, StatusEnum.UNKNOWN, "K8S DataX kill failed: " + e.getMessage(), null);
        }
    }

    @Override
    public boolean finish(DataxExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return true;
        }
        return kubernetesClient.cleanup(runtimeRef(param, state), DataxKubernetesCleanupMode.AFTER_FINISH);
    }

    private DataxTaskResult result(WorkerTaskExecutionState state, StatusEnum status, String message, String pluginLogPath) {
        String pluginLogUri = firstText(pluginLogPath, pluginLogUri(state));
        return DataxTaskResult.builder()
                .status(status)
                .appId(state == null ? null : state.getAppId())
                .workDirPath(state == null ? null : state.getWorkDirPath())
                .result(resultJson(message, pluginLogUri))
                .build();
    }

    private DataxKubernetesRuntimeRef runtimeRef(DataxExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            throw new IllegalArgumentException("K8S DataX runtime ref不存在");
        }
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

    private String pluginLogUri(DataxKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return runtimeRef.getLogStorageUri();
        }
        return "k8s://" + runtimeRef.getNamespace() + "/jobs/" + runtimeRef.getJobName();
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
    }

    private ObjectNode resultJson(String message, String pluginLogUri) {
        return PluginResultJson.build(message, "DATAX", DataxRunMode.K8S.name(), pluginLogUri, null);
    }

    private String firstText(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
