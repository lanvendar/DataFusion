package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkSubmitResult;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkTaskRunner;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * K8S_OPERATOR Flink task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Component
public class K8sOperatorFlinkTaskRunner implements FlinkTaskRunner {

    /**
     * Kubernetes Operator client.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * Parameter resolver.
     */
    private final FlinkParamResolver paramResolver;

    /**
     * State store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Constructor.
     *
     * @param operatorClient Operator client
     * @param paramResolver  parameter resolver
     * @param stateStore     state store
     */
    public K8sOperatorFlinkTaskRunner(K8sOperatorClient operatorClient, FlinkParamResolver paramResolver,
            WorkerTaskExecutionStore stateStore) {
        this.operatorClient = operatorClient;
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
    }

    @Override
    public FlinkRunMode runMode() {
        return FlinkRunMode.K8S_OPERATOR;
    }

    @Override
    public FlinkSubmitResult submit(TaskRequest request, FlinkExecutionParam param) {
        try {
            FlinkKubernetesRuntimeRef runtimeRef = operatorClient.submit(param);
            return FlinkSubmitResult.builder()
                    .status(StatusEnum.RUNNING)
                    .appId(runtimeRef.getDeploymentName())
                    .workDirPath(param.getWorkDir().toString())
                    .result(resultJson("K8S_OPERATOR Flink job submitted", runtimeRef))
                    .kubernetesRuntimeRef(runtimeRef)
                    .build();
        } catch (Exception e) {
            return FlinkSubmitResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .workDirPath(param.getWorkDir().toString())
                    .result(PluginResultJson.build(e.getMessage(), "FLINK", FlinkRunMode.K8S_OPERATOR.name(),
                            null, null))
                    .build();
        }
    }

    @Override
    public TaskResult stop(TaskRequest request, WorkerTaskExecutionState state) {
        FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        operatorClient.stop(runtimeRef);
        return result(request, state, StatusEnum.STOPPING, "K8S_OPERATOR Flink stop requested", null);
    }

    @Override
    public TaskResult kill(TaskRequest request, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return result(request, state, StatusEnum.KILLED, "K8S_OPERATOR Flink runtime ref not found, nothing to kill",
                    null);
        }
        try {
            FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
            operatorClient.kill(runtimeRef);
            return result(request, state, StatusEnum.KILLED, "K8S_OPERATOR Flink kill completed", null);
        } catch (RuntimeException e) {
            return result(request, state, StatusEnum.UNKNOWN, "K8S_OPERATOR Flink kill failed: " + e.getMessage(),
                    null);
        }
    }

    private TaskResult result(TaskRequest request, WorkerTaskExecutionState state, StatusEnum status, String message,
            String pluginLogPath) {
        TaskRequest resolvedRequest = resolveRequest(request);
        FlinkKubernetesRuntimeRef runtimeRef = state == null || isBlank(state.getAppId()) ? null
                : runtimeRef(request, state);
        WorkerResult requestWorkerResult = resolvedRequest.getWorkerResult();
        String pluginLogUri = firstText(pluginLogPath, firstText(pluginLogUri(state),
                runtimeRef == null ? null : pluginLogUri(runtimeRef)));
        String appId = state == null ? null : state.getAppId();
        return TaskResult.builder()
                .taskInstanceId(resolvedRequest.getTaskInstanceId())
                .flowInstanceId(resolvedRequest.getFlowInstanceId())
                .taskName(resolvedRequest.getTaskName())
                .taskState(status)
                .workerResult(WorkerResult.builder()
                        .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                        .appId(appId)
                        .workDirPath(state == null ? null : state.getWorkDirPath())
                        .message(message)
                        .pluginLogUri(pluginLogUri)
                        .build())
                .build();
    }

    private FlinkKubernetesRuntimeRef runtimeRef(TaskRequest request, WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            throw new IllegalArgumentException("K8S_OPERATOR Flink runtime ref不存在");
        }
        FlinkExecutionParam param = paramResolver.resolve(resolveRequest(request));
        return FlinkKubernetesRuntimeRef.builder()
                .namespace(param.getKubernetes().getNamespace())
                .deploymentName(state.getAppId())
                .secretName(param.getKubernetes().getSecretName())
                .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                .logStorageUri(param.getKubernetes().getLogStorageUri())
                .flinkWebUiUri(param.getKubernetes().getFlinkWebUiUri())
                .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                .deleteDeploymentOnFinish(param.getKubernetes().isDeleteDeploymentOnFinish())
                .deleteSecretOnFinish(param.getKubernetes().isDeleteSecretOnFinish())
                .build();
    }

    private TaskRequest resolveRequest(TaskRequest request) {
        if (request != null && request.getPluginParam() != null && request.getTaskData() != null) {
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
        resolvedRequest.setWorkerResult(request.getWorkerResult());
        resolvedRequest.setTaskState(request.getTaskState());
        resolvedRequest.setSubmitMode(request.getSubmitMode());
        resolvedRequest.setTaskData(request.getTaskData() == null ? snapshot.getTaskData() : request.getTaskData());
        resolvedRequest.setPluginParam(request.getPluginParam() == null ? snapshot.getPluginParam()
                : request.getPluginParam());
        return resolvedRequest;
    }

    private ObjectNode resultJson(String message, FlinkKubernetesRuntimeRef runtimeRef) {
        ObjectNode result = PluginResultJson.build(message, "FLINK", FlinkRunMode.K8S_OPERATOR.name(),
                pluginLogUri(runtimeRef), null);
        result.put("flinkWebUiUri", runtimeRef.getFlinkWebUiUri());
        return result;
    }

    private String pluginLogUri(FlinkKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return runtimeRef.getLogStorageUri();
        }
        return "k8s-operator://" + runtimeRef.getNamespace() + "/flinkdeployments/"
                + runtimeRef.getDeploymentName();
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
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
