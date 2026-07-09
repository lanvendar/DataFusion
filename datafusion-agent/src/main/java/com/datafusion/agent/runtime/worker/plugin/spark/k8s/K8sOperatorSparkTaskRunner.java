package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkRunMode;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkTaskResult;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkTaskRunner;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * K8S_OPERATOR Spark 任务运行器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Slf4j
@Component
public class K8sOperatorSparkTaskRunner implements SparkTaskRunner {

    /**
     * Operator 客户端.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * 状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 构造函数.
     *
     * @param operatorClient Operator 客户端
     * @param stateStore     状态存储
     */
    public K8sOperatorSparkTaskRunner(K8sOperatorClient operatorClient, WorkerTaskExecutionStore stateStore) {
        this.operatorClient = operatorClient;
        this.stateStore = stateStore;
    }

    @Override
    public SparkRunMode runMode() {
        return SparkRunMode.K8S_OPERATOR;
    }

    @Override
    public SparkTaskResult submit(SparkExecutionParam param) {
        WorkerTaskExecutionState oldState = stateStore.readState(param.getTaskInstanceId()).orElse(null);
        if (oldState != null && !isBlank(oldState.getAppId())) {
            SparkKubernetesRuntimeRef oldRuntimeRef = runtimeRef(param, oldState);
            if (!operatorClient.cleanup(oldRuntimeRef)) {
                return result(oldState, StatusEnum.SUBMIT_FAILURE, "SPARK_K8S_OPERATOR cleanup before submit failed",
                        null);
            }
        }
        try {
            log.info("SPARK_K8S_OPERATOR任务开始提交, taskInstanceId={}, namespace={}, applicationName={}",
                    param.getTaskInstanceId(), param.getKubernetes().getNamespace(),
                    param.getKubernetes().getApplicationName());
            SparkKubernetesRuntimeRef runtimeRef = operatorClient.submit(param);
            return SparkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_SUCCESS)
                    .appId(runtimeRef.getApplicationName())
                    .workDirPath(param.getWorkDir().toString())
                    .result(resultJson("SPARK_K8S_OPERATOR SparkApplication submitted", runtimeRef))
                    .build();
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR任务提交失败, taskInstanceId={}, error={}",
                    param.getTaskInstanceId(), e.getMessage());
            return SparkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .workDirPath(param.getWorkDir().toString())
                    .result(PluginResultJson.build(e.getMessage(), "SPARK", SparkRunMode.K8S_OPERATOR.name(),
                            null, null))
                    .build();
        }
    }

    @Override
    public SparkTaskResult stop(SparkExecutionParam param, WorkerTaskExecutionState state) {
        SparkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        operatorClient.stop(runtimeRef);
        return result(state, StatusEnum.STOPPING, "SPARK_K8S_OPERATOR stop requested", null);
    }

    @Override
    public SparkTaskResult kill(SparkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return result(state, StatusEnum.KILLED, "SPARK_K8S_OPERATOR runtime ref not found, nothing to kill", null);
        }
        try {
            SparkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            operatorClient.kill(runtimeRef);
            return result(state, StatusEnum.KILLED, "SPARK_K8S_OPERATOR kill completed", null);
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR强杀失败, taskInstanceId={}, appId={}, error={}",
                    param.getTaskInstanceId(), state.getAppId(), e.getMessage());
            return result(state, StatusEnum.UNKNOWN, "SPARK_K8S_OPERATOR kill failed: " + e.getMessage(), null);
        }
    }

    @Override
    public boolean finish(SparkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return true;
        }
        return operatorClient.cleanup(runtimeRef(param, state));
    }

    private SparkTaskResult result(WorkerTaskExecutionState state, StatusEnum status, String message, String pluginLogPath) {
        String pluginLogUri = firstText(pluginLogPath, pluginLogUri(state));
        return SparkTaskResult.builder()
                .status(status)
                .appId(state == null ? null : state.getAppId())
                .workDirPath(state == null ? null : state.getWorkDirPath())
                .result(resultJson(message, null, pluginLogUri))
                .build();
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

    private ObjectNode resultJson(String message, SparkKubernetesRuntimeRef runtimeRef) {
        return resultJson(message, runtimeRef, pluginLogUri(runtimeRef));
    }

    private ObjectNode resultJson(String message, SparkKubernetesRuntimeRef runtimeRef, String pluginLogUri) {
        ObjectNode result = PluginResultJson.build(message, "SPARK", SparkRunMode.K8S_OPERATOR.name(), pluginLogUri,
                null);
        if (runtimeRef != null) {
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
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
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
