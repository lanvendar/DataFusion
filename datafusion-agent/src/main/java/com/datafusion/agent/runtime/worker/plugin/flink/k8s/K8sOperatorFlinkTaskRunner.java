package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkTaskResult;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkTaskRunner;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * K8S_OPERATOR Flink task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Slf4j
@Component
public class K8sOperatorFlinkTaskRunner implements FlinkTaskRunner {

    /**
     * Kubernetes Operator client.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * Constructor.
     *
     * @param operatorClient Operator client
     */
    public K8sOperatorFlinkTaskRunner(K8sOperatorClient operatorClient) {
        this.operatorClient = operatorClient;
    }

    @Override
    public FlinkRunMode runMode() {
        return FlinkRunMode.K8S_OPERATOR;
    }

    @Override
    public FlinkTaskResult submit(FlinkExecutionParam param) {
        try {
            log.info("K8S_OPERATOR Flink任务开始提交, taskInstanceId={}, flowInstanceId={}, namespace={}, "
                            + "deploymentName={}, flinkAppDir={}, flinkAppJar={}",
                    param.getTaskInstanceId(), param.getFlowInstanceId(), param.getKubernetes().getNamespace(),
                    param.getKubernetes().getDeploymentName(), param.getKubernetes().getFlinkAppDir(),
                    param.getFlinkAppJar());
            FlinkKubernetesRuntimeRef runtimeRef = operatorClient.submit(param);
            log.info("K8S_OPERATOR Flink任务提交成功, taskInstanceId={}, namespace={}, deploymentName={}, "
                            + "flinkWebUiUri={}, workDirPath={}",
                    param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName(),
                    runtimeRef.getFlinkWebUiUri(), param.getWorkDir());
            return FlinkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_SUCCESS)
                    .appId(runtimeRef.getDeploymentName())
                    .workDirPath(param.getWorkDir().toString())
                    .result(resultJson("K8S_OPERATOR Flink job submitted", runtimeRef))
                    .kubernetesRuntimeRef(runtimeRef)
                    .build();
        } catch (Exception e) {
            log.warn("K8S_OPERATOR Flink任务提交失败, taskInstanceId={}, flowInstanceId={}, workDirPath={}",
                    param.getTaskInstanceId(), param.getFlowInstanceId(), param.getWorkDir(), e);
            return FlinkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .workDirPath(param.getWorkDir().toString())
                    .result(PluginResultJson.build(e.getMessage(), "FLINK", FlinkRunMode.K8S_OPERATOR.name(),
                            null, null))
                    .build();
        }
    }

    @Override
    public FlinkTaskResult stop(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        log.info("K8S_OPERATOR Flink任务请求停止, taskInstanceId={}, namespace={}, deploymentName={}",
                param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
        operatorClient.stop(runtimeRef);
        return result(param, state, StatusEnum.STOPPING, "K8S_OPERATOR Flink stop requested", null);
    }

    @Override
    public FlinkTaskResult kill(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return result(param, state, StatusEnum.KILLED, "K8S_OPERATOR Flink runtime ref not found, nothing to kill",
                    null);
        }
        try {
            FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            log.info("K8S_OPERATOR Flink任务请求强杀, taskInstanceId={}, namespace={}, deploymentName={}",
                    param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
            operatorClient.kill(runtimeRef);
            return result(param, state, StatusEnum.KILLED, "K8S_OPERATOR Flink kill completed", null);
        } catch (RuntimeException e) {
            log.warn("K8S_OPERATOR Flink任务强杀失败, taskInstanceId={}, appId={}",
                    param.getTaskInstanceId(), state.getAppId(), e);
            return result(param, state, StatusEnum.UNKNOWN, "K8S_OPERATOR Flink kill failed: " + e.getMessage(),
                    null);
        }
    }

    @Override
    public boolean finish(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return true;
        }
        return operatorClient.cleanup(runtimeRef(param, state));
    }

    private FlinkTaskResult result(FlinkExecutionParam param, WorkerTaskExecutionState state, StatusEnum status, String message,
            String pluginLogPath) {
        FlinkKubernetesRuntimeRef runtimeRef = state == null || isBlank(state.getAppId()) ? null : runtimeRef(param, state);
        String pluginLogUri = firstText(pluginLogPath, firstText(pluginLogUri(state),
                runtimeRef == null ? null : pluginLogUri(runtimeRef)));
        return FlinkTaskResult.builder()
                .status(status)
                .appId(state == null ? null : state.getAppId())
                .workDirPath(state == null ? null : state.getWorkDirPath())
                .result(resultJson(message, runtimeRef, pluginLogUri))
                .build();
    }

    private FlinkKubernetesRuntimeRef runtimeRef(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            throw new IllegalArgumentException("K8S_OPERATOR Flink runtime ref不存在");
        }
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

    private ObjectNode resultJson(String message, FlinkKubernetesRuntimeRef runtimeRef) {
        return resultJson(message, runtimeRef, pluginLogUri(runtimeRef));
    }

    private ObjectNode resultJson(String message, FlinkKubernetesRuntimeRef runtimeRef, String pluginLogUri) {
        ObjectNode result = PluginResultJson.build(message, "FLINK", FlinkRunMode.K8S_OPERATOR.name(), pluginLogUri, null);
        if (runtimeRef != null) {
            result.put("flinkWebUiUri", runtimeRef.getFlinkWebUiUri());
        }
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
