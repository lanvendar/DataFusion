package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
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
}
