package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * DataX K8S run mode state mapping.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class DataxK8sRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Kubernetes client.
     */
    private final DataxKubernetesClient kubernetesClient;

    /**
     * Constructor.
     *
     * @param kubernetesClient Kubernetes client
     */
    public DataxK8sRunModeStateMapping(DataxKubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
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
        if (state == null || state.getPluginParam() == null
                || !state.getPluginParam().hasNonNull(DataxExecutionParam.RUNTIME_FIELD)) {
            return StatusEnum.UNKNOWN;
        }
        DataxKubernetesRuntimeRef runtimeRef = OBJECT_MAPPER.convertValue(
                state.getPluginParam().get(DataxExecutionParam.RUNTIME_FIELD), DataxKubernetesRuntimeRef.class);
        return kubernetesClient.queryStatus(runtimeRef, state.getStatus());
    }
}
