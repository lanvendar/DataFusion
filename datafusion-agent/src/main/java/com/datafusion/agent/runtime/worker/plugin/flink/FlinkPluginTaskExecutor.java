package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesRuntimeRef;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.K8sOperatorClient;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Flink 插件任务执行器.
 *
 * <p>执行器只修改动作级上下文中的候选运行态，不读取或写入任务执行存储。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Component
@Slf4j
public class FlinkPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * 插件类型.
     */
    public static final String PLUGIN_TYPE = "FLINK";

    /**
     * 参数解析器.
     */
    private final FlinkParamResolver paramResolver;

    /**
     * Operator 客户端.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * 构造函数.
     *
     * @param paramResolver  参数解析器
     * @param operatorClient Operator 客户端
     */
    public FlinkPluginTaskExecutor(FlinkParamResolver paramResolver, K8sOperatorClient operatorClient) {
        this.paramResolver = paramResolver;
        this.operatorClient = operatorClient;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return FlinkRunMode.K8S_OPERATOR.name();
    }

    @Override
    public void validate(RunningTaskContext context) {
        resolve(context);
    }

    @Override
    public WorkerResult submit(RunningTaskContext context) {
        FlinkExecutionParam param = resolve(context);
        WorkerTaskExecutionState state = context.getExecutionState();
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
                    runtimeRef.getFlinkWebUiUri(), context.getWorkDirPath());
            applyResult(state, StatusEnum.SUBMIT_SUCCESS, runtimeRef.getDeploymentName(), context.getWorkDirPath(),
                    resultJson("K8S_OPERATOR Flink job submitted", runtimeRef, pluginLogUri(runtimeRef)));
        } catch (RuntimeException e) {
            log.warn("K8S_OPERATOR Flink任务提交失败, taskInstanceId={}, flowInstanceId={}, workDirPath={}",
                    param.getTaskInstanceId(), param.getFlowInstanceId(), context.getWorkDirPath(), e);
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                    PluginResultJson.build(e.getMessage(), PLUGIN_TYPE, runMode(), null, null));
        }
        return workerResult(state);
    }

    @Override
    public WorkerResult stop(RunningTaskContext context) {
        FlinkExecutionParam param = resolve(context);
        WorkerTaskExecutionState state = context.getExecutionState();
        try {
            FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            log.info("K8S_OPERATOR Flink任务请求停止, taskInstanceId={}, namespace={}, deploymentName={}",
                    param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
            operatorClient.stop(runtimeRef);
            applyControlResult(state, StatusEnum.STOPPING, "K8S_OPERATOR Flink stop requested", runtimeRef,
                    context.getWorkDirPath());
        } catch (RuntimeException e) {
            log.warn("K8S_OPERATOR Flink任务停止失败, taskInstanceId={}, appId={}",
                    param.getTaskInstanceId(), state.getAppId(), e);
            applyControlResult(state, StatusEnum.STOP_FAILURE,
                    "K8S_OPERATOR Flink stop failed: " + e.getMessage(), null, context.getWorkDirPath());
        }
        return workerResult(state);
    }

    @Override
    public WorkerResult kill(RunningTaskContext context) {
        FlinkExecutionParam param = resolve(context);
        WorkerTaskExecutionState state = context.getExecutionState();
        if (isBlank(state.getAppId())) {
            applyControlResult(state, StatusEnum.KILLED,
                    "K8S_OPERATOR Flink runtime ref not found, nothing to kill", null, context.getWorkDirPath());
            return workerResult(state);
        }
        try {
            FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            log.info("K8S_OPERATOR Flink任务请求强杀, taskInstanceId={}, namespace={}, deploymentName={}",
                    param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
            operatorClient.kill(runtimeRef);
            applyControlResult(state, StatusEnum.KILLING, "K8S_OPERATOR Flink kill requested", runtimeRef,
                    context.getWorkDirPath());
        } catch (RuntimeException e) {
            log.warn("K8S_OPERATOR Flink任务强杀失败, taskInstanceId={}, appId={}",
                    param.getTaskInstanceId(), state.getAppId(), e);
            applyControlResult(state, StatusEnum.UNKNOWN,
                    "K8S_OPERATOR Flink kill failed: " + e.getMessage(), null, context.getWorkDirPath());
        }
        return workerResult(state);
    }

    @Override
    public boolean finish(RunningTaskContext context) {
        WorkerTaskExecutionState state = context.getExecutionState();
        if (context.getSnapshot().getPluginParam() == null || isBlank(state.getAppId())) {
            return true;
        }
        FlinkExecutionParam param = resolve(context);
        return operatorClient.cleanup(runtimeRef(param, state));
    }

    private FlinkExecutionParam resolve(RunningTaskContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context不能为空");
        }
        return paramResolver.resolve(context.getSnapshot(), context.getWorkDirPath());
    }

    private void applyControlResult(WorkerTaskExecutionState state, StatusEnum status, String message,
            FlinkKubernetesRuntimeRef runtimeRef, String workDirPath) {
        String pluginLogUri = firstText(pluginLogUri(state),
                runtimeRef == null ? null : pluginLogUri(runtimeRef));
        applyResult(state, status, state.getAppId(), firstText(state.getWorkDirPath(), workDirPath),
                resultJson(message, runtimeRef, pluginLogUri));
    }

    private void applyResult(WorkerTaskExecutionState state, StatusEnum status, String appId, String workDirPath,
            JsonNode result) {
        state.setStatus(status);
        state.setAppId(appId);
        state.setWorkDirPath(workDirPath);
        state.setResult(result);
    }

    private WorkerResult workerResult(WorkerTaskExecutionState state) {
        return WorkerResult.builder()
                .outputVars(state.getOutputVars())
                .workerId(state.getWorkerId())
                .appId(state.getAppId())
                .workDirPath(state.getWorkDirPath())
                .message(resultText(state.getResult(), "message"))
                .pluginLogUri(resultText(state.getResult(), "pluginLogUri"))
                .build();
    }

    private String resultText(JsonNode result, String fieldName) {
        if (result == null || !result.hasNonNull(fieldName)) {
            return null;
        }
        return result.get(fieldName).asText();
    }

    private FlinkKubernetesRuntimeRef runtimeRef(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        if (isBlank(state.getAppId())) {
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

    private ObjectNode resultJson(String message, FlinkKubernetesRuntimeRef runtimeRef, String pluginLogUri) {
        ObjectNode result = PluginResultJson.build(message, PLUGIN_TYPE, runMode(), pluginLogUri, null);
        if (runtimeRef != null && !isBlank(runtimeRef.getFlinkWebUiUri())) {
            result.put("flinkWebUiUri", runtimeRef.getFlinkWebUiUri());
        }
        return result;
    }

    private String pluginLogUri(FlinkKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        return "k8s-operator://" + runtimeRef.getNamespace() + "/flinkdeployments/"
                + runtimeRef.getDeploymentName();
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        return resultText(state == null ? null : state.getResult(), "pluginLogUri");
    }

    private String firstText(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
