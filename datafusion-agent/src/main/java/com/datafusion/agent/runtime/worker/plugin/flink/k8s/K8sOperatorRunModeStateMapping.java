package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * K8S_OPERATOR Flink 运行模式状态映射.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Slf4j
@Component
public class K8sOperatorRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Operator 客户端.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * 参数解析器.
     */
    private final FlinkParamResolver paramResolver;

    /**
     * 构造函数.
     *
     * @param operatorClient Operator 客户端
     * @param paramResolver  参数解析器
     */
    public K8sOperatorRunModeStateMapping(K8sOperatorClient operatorClient, FlinkParamResolver paramResolver) {
        this.operatorClient = operatorClient;
        this.paramResolver = paramResolver;
    }

    @Override
    public String pluginType() {
        return FlinkPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return FlinkRunMode.K8S_OPERATOR.name();
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        StatusEnum taskState = state.getStatus();
        if (state.getAppId() == null) {
            log.warn("FLINK_K8S_OPERATOR appId不存在, taskInstanceId={}, taskState={}",
                    state.getTaskInstanceId(), taskState);
            return StatusEnum.UNKNOWN;
        }
        if (!(taskState.isSubmitting() || taskState == StatusEnum.RUNNING
                || taskState == StatusEnum.STOPPING || taskState == StatusEnum.KILLING)) {
            log.warn("FLINK_K8S_OPERATOR 任务状态不支持映射, taskInstanceId={}, appId={}, taskState={}",
                    state.getTaskInstanceId(), state.getAppId(), taskState);
            return StatusEnum.UNKNOWN;
        }
        FlinkExecutionParam param = paramResolver.resolve(snapshot, state.getWorkDirPath());
        FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        FlinkOperatorStatus operatorStatus = operatorClient.queryStatus(runtimeRef);
        return mapOperatorStatus(operatorStatus, state, runtimeRef);
    }

    @Override
    public boolean prepareFinalReport(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        if (isFinalized(state)) {
            return false;
        }
        FlinkExecutionParam param = paramResolver.resolve(snapshot, state.getWorkDirPath());
        FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        String pluginLogUri = collectLogs(state, runtimeRef);
        ObjectNode result = PluginResultJson.build("FLINK_K8S_OPERATOR Flink task finished", pluginType(), runMode(),
                pluginLogUri, null);
        result.put("flinkWebUiUri", runtimeRef.getFlinkWebUiUri());
        result.put("finalized", true);
        state.setResult(result);
        return true;
    }

    private FlinkKubernetesRuntimeRef runtimeRef(FlinkExecutionParam param, WorkerTaskExecutionState state) {
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

    /**
     * 映射 Operator 状态.
     *
     * @param operatorStatus Operator 状态快照
     * @param state          任务执行状态
     * @param runtimeRef     Kubernetes 运行引用
     * @return DataFusion 任务状态
     */
    private StatusEnum mapOperatorStatus(FlinkOperatorStatus operatorStatus, WorkerTaskExecutionState state,
            FlinkKubernetesRuntimeRef runtimeRef) {
        StatusEnum taskState = state.getStatus();
        if (operatorStatus == null || operatorStatus.getState() == null) {
            log.warn("FLINK_K8S_OPERATOR Operator状态为空, taskInstanceId={}, appId={}, taskState={}",
                    state.getTaskInstanceId(), state.getAppId(), taskState);
            return StatusEnum.UNKNOWN;
        }
        if (taskState != StatusEnum.STOPPING && taskState != StatusEnum.KILLING
                && !operatorStatus.isDeploymentExists()) {
            log.warn("FLINK_K8S_OPERATOR FlinkDeployment不存在, taskInstanceId={}, appId={},"
                            + " taskState={}, operatorState={}",
                    state.getTaskInstanceId(), state.getAppId(), taskState, operatorStatus.getState());
            return StatusEnum.UNKNOWN;
        }
        return switch (taskState) {
            case SUBMITTING, SUBMIT_SUCCESS, RUNNING -> mapExecutionState(operatorStatus, state);
            case STOPPING -> mapStoppingState(operatorStatus, state, runtimeRef);
            case KILLING -> mapKillingState(operatorStatus, runtimeRef);
            default -> {
                log.warn("FLINK_K8S_OPERATOR 任务状态不支持映射, taskInstanceId={}, appId={},"
                                + " taskState={}, operatorState={}",
                        state.getTaskInstanceId(), state.getAppId(), taskState, operatorStatus.getState());
                yield StatusEnum.UNKNOWN;
            }
        };
    }

    /**
     * 映射正常执行状态.
     *
     * @param operatorStatus Operator 状态
     * @param state          任务执行状态
     * @return DataFusion 任务状态
     */
    private StatusEnum mapExecutionState(FlinkOperatorStatus operatorStatus, WorkerTaskExecutionState state) {
        if (operatorStatus.isReconciliationPending()) {
            return state.getStatus();
        }
        return switch (operatorStatus.getState()) {
            case NONE, CREATED, INITIALIZING, RECONCILING -> mapPendingState(operatorStatus, state);
            case RUNNING, RESTARTING, FAILING, CANCELLING -> StatusEnum.RUNNING;
            case FINISHED -> StatusEnum.RUN_SUCCESS;
            case FAILED, CANCELED, SUSPENDED -> StatusEnum.RUN_FAILURE;
            case UNKNOWN -> {
                log.warn("FLINK_K8S_OPERATOR Operator状态未知, taskInstanceId={}, appId={},"
                                + " taskState={}, operatorState={}",
                        state.getTaskInstanceId(), state.getAppId(), state.getStatus(), operatorStatus.getState());
                yield operatorStatus.isJobManagerError()
                        ? mapPendingState(operatorStatus, state) : StatusEnum.UNKNOWN;
            }
        };
    }

    /**
     * 映射停止阶段 Operator 状态.
     *
     * @param operatorStatus Operator 状态快照
     * @param state          任务执行状态
     * @param runtimeRef     Kubernetes 运行引用
     * @return DataFusion 任务状态
     */
    private StatusEnum mapStoppingState(FlinkOperatorStatus operatorStatus, WorkerTaskExecutionState state,
            FlinkKubernetesRuntimeRef runtimeRef) {
        if (!operatorStatus.isDeploymentExists()) {
            return operatorClient.runtimePodsExist(runtimeRef) ? StatusEnum.STOPPING : StatusEnum.STOP_SUCCESS;
        }
        if (operatorStatus.getDesiredState() != FlinkOperatorStatus.State.SUSPENDED
                || operatorStatus.isReconciliationPending()) {
            return StatusEnum.STOPPING;
        }
        FlinkOperatorStatus.State operatorState = operatorStatus.getState();
        return switch (operatorState) {
            case FINISHED, CANCELED, SUSPENDED -> StatusEnum.STOP_SUCCESS;
            case FAILED -> StatusEnum.STOP_FAILURE;
            case UNKNOWN -> {
                log.warn("FLINK_K8S_OPERATOR 停止状态未知, taskInstanceId={}, appId={}, jobManagerState={}",
                        state.getTaskInstanceId(), state.getAppId(), operatorStatus.getJobManagerState());
                yield operatorStatus.isJobManagerError()
                        ? StatusEnum.STOP_FAILURE : StatusEnum.UNKNOWN;
            }
            default -> operatorStatus.isJobManagerError()
                    ? StatusEnum.STOP_FAILURE : StatusEnum.STOPPING;
        };
    }

    /**
     * 映射 Operator 尚未给出明确作业状态的阶段.
     *
     * @param operatorStatus Operator 状态快照
     * @param state          任务执行状态
     * @return DataFusion 任务状态
     */
    private StatusEnum mapPendingState(FlinkOperatorStatus operatorStatus, WorkerTaskExecutionState state) {
        if (!operatorStatus.isJobManagerError()) {
            return state.getStatus();
        }
        return state.getStatus().isSubmitting() ? StatusEnum.SUBMIT_FAILURE : StatusEnum.RUN_FAILURE;
    }

    /**
     * 映射强杀阶段状态.
     *
     * @param operatorStatus Operator 状态快照
     * @param runtimeRef     Kubernetes 运行引用
     * @return DataFusion 任务状态
     */
    private StatusEnum mapKillingState(FlinkOperatorStatus operatorStatus, FlinkKubernetesRuntimeRef runtimeRef) {
        if (operatorStatus.isDeploymentExists()) {
            return StatusEnum.KILLING;
        }
        return operatorClient.runtimePodsExist(runtimeRef) ? StatusEnum.KILLING : StatusEnum.KILLED;
    }

    private String collectLogs(WorkerTaskExecutionState state, FlinkKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = operatorClient.collectLogs(runtimeRef);
            Path logFile = Path.of(state.getWorkDirPath()).resolve("k8s-flink.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            log.warn("采集K8S_OPERATOR Flink日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
            return firstText(pluginLogUri(state), pluginLogUri(runtimeRef));
        }
    }

    private String pluginLogUri(FlinkKubernetesRuntimeRef runtimeRef) {
        return "k8s-operator://" + runtimeRef.getNamespace() + "/flinkdeployments/"
                + runtimeRef.getDeploymentName();
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
