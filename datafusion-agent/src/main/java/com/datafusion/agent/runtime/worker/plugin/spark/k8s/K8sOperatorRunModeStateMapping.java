package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spark K8S_OPERATOR 状态映射.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Slf4j
@Component("sparkK8sOperatorRunModeStateMapping")
public class K8sOperatorRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Operator 客户端.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * 参数解析器.
     */
    private final SparkParamResolver paramResolver;

    /**
     * 状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 构造函数.
     *
     * @param operatorClient Operator 客户端
     * @param paramResolver  参数解析器
     * @param stateStore     状态存储
     */
    public K8sOperatorRunModeStateMapping(K8sOperatorClient operatorClient,
            SparkParamResolver paramResolver, WorkerTaskExecutionStore stateStore) {
        this.operatorClient = operatorClient;
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
    }

    @Override
    public String pluginType() {
        return SparkPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return SparkRunMode.K8S_OPERATOR.name();
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            log.warn("SPARK_K8S_OPERATOR 状态为空或appId不存在, taskState={}", state);
            return StatusEnum.UNKNOWN;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        if (snapshot == null) {
            log.warn("SPARK_K8S_OPERATOR 执行快照snap不存在, taskInstanceId={}, appId={}, localState={}",
                    state.getTaskInstanceId(), state.getAppId(), state.getStatus());
            return StatusEnum.UNKNOWN;
        }
        SparkExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        return mapKubernetesStatus(operatorClient.queryStatus(runtimeRef(param, state)), state);
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
        SparkExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        SparkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        String pluginLogUri = collectLogs(state, param, runtimeRef);
        ObjectNode result = PluginResultJson.build("SPARK_K8S_OPERATOR Spark task finished", pluginType(), runMode(),
                pluginLogUri, null);
        result.put("sparkWebUiUri", runtimeRef.getSparkWebUiUri());
        result.put("finalized", true);
        state.setResult(result);
        stateStore.saveState(state);
    }

    private SparkKubernetesRuntimeRef runtimeRef(SparkExecutionParam param, WorkerTaskExecutionState state) {
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

    /**
     * 映射 Kubernetes 状态.
     *
     * @param kubernetesStatus Kubernetes 状态
     * @param state            任务执行状态
     * @return DataFusion 状态
     */
    private StatusEnum mapKubernetesStatus(SparkOperatorStatus kubernetesStatus, WorkerTaskExecutionState state) {
        StatusEnum localState = state.getStatus();
        if (localState != null && localState.isFinalState()) {
            return localState;
        }
        if (kubernetesStatus == null || kubernetesStatus.getState() == null) {
            log.warn("SPARK_K8S_OPERATOR SparkApplication状态为空, taskInstanceId={}, appId={}, localState={}",
                    state.getTaskInstanceId(), state.getAppId(), localState);
            return StatusEnum.UNKNOWN;
        }
        if (localState == StatusEnum.KILLING) {
            return kubernetesStatus.isApplicationExists() || kubernetesStatus.isPodExists()
                    || kubernetesStatus.isServiceExists() ? StatusEnum.KILLING : StatusEnum.KILLED;
        }
        if (!kubernetesStatus.isApplicationExists()) {
            log.warn("SPARK_K8S_OPERATOR SparkApplication不存在, taskInstanceId={}, appId={},"
                            + " localState={}, sparkState={}, podExists={}, serviceExists={}",
                    state.getTaskInstanceId(), state.getAppId(), localState, kubernetesStatus.getState(),
                    kubernetesStatus.isPodExists(), kubernetesStatus.isServiceExists());
            return StatusEnum.UNKNOWN;
        }
        return switch (kubernetesStatus.getState()) {
            case NONE, SUBMITTED -> mapSubmittedState(localState, state, kubernetesStatus);
            case RUNNING, PENDING_RERUN, INVALIDATING, SUCCEEDING, FAILING, RESUMING -> StatusEnum.RUNNING;
            case COMPLETED -> StatusEnum.RUN_SUCCESS;
            case SUBMISSION_FAILED -> StatusEnum.SUBMIT_FAILURE;
            case FAILED -> StatusEnum.RUN_FAILURE;
            case SUSPENDING -> {
                if (localState == StatusEnum.STOPPING) {
                    yield StatusEnum.STOPPING;
                }
                log.warn("SPARK_K8S_OPERATOR 本地状态不支持映射, taskInstanceId={}, appId={},"
                                + " localState={}, sparkState={}",
                        state.getTaskInstanceId(), state.getAppId(), localState, kubernetesStatus.getState());
                yield StatusEnum.UNKNOWN;
            }
            case SUSPENDED -> {
                if (localState == StatusEnum.STOPPING) {
                    yield StatusEnum.STOP_SUCCESS;
                }
                log.warn("SPARK_K8S_OPERATOR 本地状态不支持映射, taskInstanceId={}, appId={},"
                                + " localState={}, sparkState={}",
                        state.getTaskInstanceId(), state.getAppId(), localState, kubernetesStatus.getState());
                yield StatusEnum.UNKNOWN;
            }
            case UNKNOWN -> {
                log.warn("SPARK_K8S_OPERATOR SparkApplication状态未知, taskInstanceId={}, appId={},"
                                + " localState={}, sparkState={}",
                        state.getTaskInstanceId(), state.getAppId(), localState, kubernetesStatus.getState());
                yield StatusEnum.UNKNOWN;
            }
        };
    }

    private StatusEnum mapSubmittedState(StatusEnum localState, WorkerTaskExecutionState state,
            SparkOperatorStatus kubernetesStatus) {
        if (localState == StatusEnum.RUNNING) {
            return StatusEnum.RUNNING;
        }
        if (localState != null && localState.isSubmitting()) {
            return StatusEnum.SUBMIT_SUCCESS;
        }
        log.warn("SPARK_K8S_OPERATOR 本地状态不支持映射, taskInstanceId={}, appId={},"
                        + " localState={}, sparkState={}",
                state.getTaskInstanceId(), state.getAppId(), localState, kubernetesStatus.getState());
        return StatusEnum.UNKNOWN;
    }

    private String collectLogs(WorkerTaskExecutionState state, SparkExecutionParam param,
            SparkKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = operatorClient.collectLogs(runtimeRef);
            Path logFile = taskRuntimeDir(state, param).resolve("k8s-spark.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            log.warn("采集SPARK_K8S_OPERATOR日志失败, taskInstanceId={}, error={}",
                    state.getTaskInstanceId(), e.getMessage());
            return firstText(pluginLogUri(state), pluginLogUri(runtimeRef));
        }
    }

    private Path taskRuntimeDir(WorkerTaskExecutionState state, SparkExecutionParam param) {
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
        request.setRunMode(snapshot.getRunMode());
        request.setTaskData(snapshot.getTaskData());
        request.setPluginParam(snapshot.getPluginParam());
        return request;
    }

    private String pluginLogUri(SparkKubernetesRuntimeRef runtimeRef) {
        return "spark-operator://" + runtimeRef.getNamespace() + "/sparkapplications/"
                + runtimeRef.getApplicationName();
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
