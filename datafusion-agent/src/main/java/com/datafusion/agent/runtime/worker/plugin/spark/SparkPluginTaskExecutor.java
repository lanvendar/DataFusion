package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.K8sOperatorClient;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesParam;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesRuntimeRef;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spark 插件任务执行器.
 *
 * <p>执行器只修改动作级上下文中的候选运行态，不读取或写入任务执行存储。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Component
@Slf4j
public class SparkPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * 插件类型.
     */
    public static final String PLUGIN_TYPE = "SPARK";

    /**
     * 参数解析器.
     */
    private final SparkParamResolver paramResolver;

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
    public SparkPluginTaskExecutor(SparkParamResolver paramResolver, K8sOperatorClient operatorClient) {
        this.paramResolver = paramResolver;
        this.operatorClient = operatorClient;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return SparkRunMode.K8S_OPERATOR.name();
    }

    @Override
    public void validate(RunningTaskContext context) {
        resolve(context);
    }

    @Override
    public WorkerResult submit(RunningTaskContext context) {
        WorkerTaskExecutionState state = context.getExecutionState();
        if (!cleanupPreviousExecution(context)) {
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                    PluginResultJson.build("SPARK_K8S_OPERATOR cleanup before submit failed", PLUGIN_TYPE,
                            runMode(), null, null));
            return workerResult(state);
        }
        SparkExecutionParam param = resolve(context);
        try {
            log.info("SPARK_K8S_OPERATOR任务开始提交, taskInstanceId={}, namespace={}, applicationName={}",
                    param.getTaskInstanceId(), param.getKubernetes().getNamespace(),
                    param.getKubernetes().getApplicationName());
            SparkKubernetesRuntimeRef runtimeRef = operatorClient.submit(param);
            applyResult(state, StatusEnum.SUBMIT_SUCCESS, runtimeRef.getApplicationName(), context.getWorkDirPath(),
                    resultJson("SPARK_K8S_OPERATOR SparkApplication submitted", runtimeRef,
                            pluginLogUri(runtimeRef)));
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR任务提交失败, taskInstanceId={}, error={}",
                    param.getTaskInstanceId(), e.getMessage());
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                    PluginResultJson.build(e.getMessage(), PLUGIN_TYPE, runMode(), null, null));
        }
        return workerResult(state);
    }

    @Override
    public WorkerResult stop(RunningTaskContext context) {
        SparkExecutionParam param = resolve(context);
        WorkerTaskExecutionState state = context.getExecutionState();
        try {
            operatorClient.stop(runtimeRef(param, state));
            applyControlResult(state, StatusEnum.STOPPING, "SPARK_K8S_OPERATOR stop requested",
                    context.getWorkDirPath());
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR停止失败, taskInstanceId={}, appId={}, error={}",
                    param.getTaskInstanceId(), state.getAppId(), e.getMessage());
            applyControlResult(state, StatusEnum.STOP_FAILURE,
                    "SPARK_K8S_OPERATOR stop failed: " + e.getMessage(), context.getWorkDirPath());
        }
        return workerResult(state);
    }

    @Override
    public WorkerResult kill(RunningTaskContext context) {
        SparkExecutionParam param = resolve(context);
        WorkerTaskExecutionState state = context.getExecutionState();
        if (isBlank(state.getAppId())) {
            applyControlResult(state, StatusEnum.KILLED,
                    "SPARK_K8S_OPERATOR runtime ref not found, nothing to kill", context.getWorkDirPath());
            return workerResult(state);
        }
        try {
            operatorClient.kill(runtimeRef(param, state));
            applyControlResult(state, StatusEnum.KILLING, "SPARK_K8S_OPERATOR kill requested",
                    context.getWorkDirPath());
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR强杀失败, taskInstanceId={}, appId={}, error={}",
                    param.getTaskInstanceId(), state.getAppId(), e.getMessage());
            applyControlResult(state, StatusEnum.UNKNOWN,
                    "SPARK_K8S_OPERATOR kill failed: " + e.getMessage(), context.getWorkDirPath());
        }
        return workerResult(state);
    }

    @Override
    public boolean finish(RunningTaskContext context) {
        WorkerTaskExecutionState state = context.getExecutionState();
        if (context.getSnapshot().getPluginParam() == null || isBlank(state.getAppId())) {
            return true;
        }
        return operatorClient.cleanup(runtimeRef(resolve(context), state));
    }

    private SparkExecutionParam resolve(RunningTaskContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context不能为空");
        }
        return paramResolver.resolve(context.getSnapshot(), context.getWorkDirPath());
    }

    private boolean cleanupPreviousExecution(RunningTaskContext context) {
        WorkerTaskExecutionSnap previousSnapshot = context.getPreviousSnapshot();
        WorkerTaskExecutionState previousState = context.getPreviousState();
        if (previousSnapshot == null || previousState == null || isBlank(previousState.getAppId())) {
            return true;
        }
        try {
            SparkExecutionParam previousParam = paramResolver.resolve(previousSnapshot, previousState.getWorkDirPath());
            return operatorClient.cleanup(runtimeRef(previousParam, previousState));
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR重提前清理失败, taskInstanceId={}, appId={}, error={}",
                    previousState.getTaskInstanceId(), previousState.getAppId(), e.getMessage());
            return false;
        }
    }

    private void applyControlResult(WorkerTaskExecutionState state, StatusEnum status, String message,
            String workDirPath) {
        applyResult(state, status, state.getAppId(), firstText(state.getWorkDirPath(), workDirPath),
                resultJson(message, null, pluginLogUri(state)));
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

    private SparkKubernetesRuntimeRef runtimeRef(SparkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            throw new IllegalArgumentException("SPARK_K8S_OPERATOR runtime ref不存在");
        }
        SparkKubernetesParam kubernetes = param.getKubernetes();
        return SparkKubernetesRuntimeRef.builder()
                .namespace(kubernetes.getNamespace())
                .applicationName(state.getAppId())
                .configMapName(kubernetes.getConfigMapName())
                .podLabelSelector(kubernetes.getPodLabelSelector())
                .logStorageUri(kubernetes.getLogStorageUri())
                .sparkWebUiUri(kubernetes.getSparkWebUiUri())
                .collectLogsOnFinish(kubernetes.isCollectLogsOnFinish())
                .build();
    }

    private ObjectNode resultJson(String message, SparkKubernetesRuntimeRef runtimeRef, String pluginLogUri) {
        ObjectNode result = PluginResultJson.build(message, PLUGIN_TYPE, runMode(), pluginLogUri, null);
        if (runtimeRef != null && !isBlank(runtimeRef.getSparkWebUiUri())) {
            result.put("sparkWebUiUri", runtimeRef.getSparkWebUiUri());
        }
        return result;
    }

    private String pluginLogUri(SparkKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef == null) {
            return null;
        }
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        return "spark-operator://" + runtimeRef.getNamespace() + "/sparkapplications/"
                + runtimeRef.getApplicationName();
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
