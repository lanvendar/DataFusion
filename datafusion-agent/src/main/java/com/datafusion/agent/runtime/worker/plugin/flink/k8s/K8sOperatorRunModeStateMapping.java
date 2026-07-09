package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Operator 客户端.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * 参数解析器.
     */
    private final FlinkParamResolver paramResolver;

    /**
     * Worker 任务执行存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 构造函数.
     *
     * @param operatorClient Operator 客户端
     * @param paramResolver  参数解析器
     * @param stateStore     Worker 任务执行存储
     */
    public K8sOperatorRunModeStateMapping(K8sOperatorClient operatorClient, FlinkParamResolver paramResolver,
            WorkerTaskExecutionStore stateStore) {
        this.operatorClient = operatorClient;
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
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
    public StatusEnum mapState(WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            log.warn("FLINK_K8S_OPERATOR 状态为空或appId不存在, taskState={}", state);
            return StatusEnum.UNKNOWN;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        if (snapshot == null) {
            log.warn("FLINK_K8S_OPERATOR  执行快照snap不存在, taskInstanceId={}, appId={}, localState={}",
                    state.getTaskInstanceId(), state.getAppId(), state.getStatus());
            return StatusEnum.UNKNOWN;
        }
        FlinkExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        return mapOperatorStatus(operatorClient.queryStatus(runtimeRef(param, state)), state);
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
        FlinkExecutionParam param = paramResolver.resolve(taskRequest(snapshot));
        FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        String pluginLogUri = collectLogs(state, param, runtimeRef);
        ObjectNode result = PluginResultJson.build("FLINK_K8S_OPERATOR Flink task finished", pluginType(), runMode(),
                pluginLogUri, null);
        result.put("flinkWebUiUri", runtimeRef.getFlinkWebUiUri());
        result.put("finalized", true);
        state.setResult(result);
        stateStore.saveState(state);
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

    private StatusEnum mapOperatorStatus(FlinkOperatorStatus operatorStatus, WorkerTaskExecutionState state) {
        StatusEnum localState = state.getStatus();
        if (localState != null && localState.isFinalState()) {
            return localState;
        }
        if (operatorStatus == null || operatorStatus.getState() == null) {
            log.warn("FLINK_K8S_OPERATOR Operator状态为空, taskInstanceId={}, appId={}, localState={}",
                    state.getTaskInstanceId(), state.getAppId(), localState);
            return StatusEnum.UNKNOWN;
        }
        if (localState == StatusEnum.KILLING) {
            return operatorStatus.isDeploymentExists() || operatorStatus.isPodExists() || operatorStatus.isServiceExists()
                    ? StatusEnum.KILLING : StatusEnum.KILLED;
        }
        if (localState == StatusEnum.STOPPING && !operatorStatus.isDeploymentExists() && !operatorStatus.isPodExists()) {
            return StatusEnum.STOP_SUCCESS;
        }
        if (!operatorStatus.isDeploymentExists()) {
            log.warn("FLINK_K8S_OPERATOR FlinkDeployment不存在, taskInstanceId={}, appId={},"
                            + " localState={}, operatorState={}, podExists={}, serviceExists={}",
                    state.getTaskInstanceId(), state.getAppId(), localState, operatorStatus.getState(),
                    operatorStatus.isPodExists(), operatorStatus.isServiceExists());
            return StatusEnum.UNKNOWN;
        }
        if (localState != null && localState.isSubmitting()) {
            return mapSubmittingState(operatorStatus.getState(), state);
        }
        if (localState == StatusEnum.RUNNING) {
            return mapRunningState(operatorStatus.getState(), state);
        }
        if (localState == StatusEnum.STOPPING) {
            return mapStoppingState(operatorStatus.getState(), !operatorStatus.isPodExists(), state);
        }
        log.warn("FLINK_K8S_OPERATOR 本地状态不支持映射, taskInstanceId={}, appId={},"
                        + " localState={}, operatorState={}",
                state.getTaskInstanceId(), state.getAppId(), localState, operatorStatus.getState());
        return StatusEnum.UNKNOWN;
    }

    /**
     * 映射提交阶段 Operator 状态.
     *
     * @param operatorState Operator 状态
     * @param state         任务执行状态
     * @return DataFusion 任务状态
     */
    private StatusEnum mapSubmittingState(FlinkOperatorStatus.State operatorState, WorkerTaskExecutionState state) {
        return switch (operatorState) {
            case NONE, CREATED, INITIALIZING, RECONCILING -> StatusEnum.SUBMIT_SUCCESS;
            case RUNNING, RESTARTING, FAILING -> StatusEnum.RUNNING;
            case FINISHED -> StatusEnum.RUN_SUCCESS;
            case FAILED -> StatusEnum.RUN_FAILURE;
            case CANCELLING, SUSPENDED -> StatusEnum.STOPPING;
            case CANCELED -> StatusEnum.STOP_FAILURE;
            case UNKNOWN -> {
                log.warn("FLINK_K8S_OPERATOR 提交阶段Operator状态未知, taskInstanceId={}, appId={},"
                                + " localState={}, operatorState={}",
                        state.getTaskInstanceId(), state.getAppId(), state.getStatus(), operatorState);
                yield StatusEnum.UNKNOWN;
            }
        };
    }

    /**
     * 映射运行阶段 Operator 状态.
     *
     * @param operatorState Operator 状态
     * @param state         任务执行状态
     * @return DataFusion 任务状态
     */
    private StatusEnum mapRunningState(FlinkOperatorStatus.State operatorState, WorkerTaskExecutionState state) {
        return switch (operatorState) {
            case NONE, RUNNING, CREATED, INITIALIZING, RECONCILING, RESTARTING, FAILING -> StatusEnum.RUNNING;
            case FINISHED -> StatusEnum.RUN_SUCCESS;
            case FAILED -> StatusEnum.RUN_FAILURE;
            case CANCELLING -> StatusEnum.STOPPING;
            case CANCELED, SUSPENDED -> StatusEnum.STOP_FAILURE;
            case UNKNOWN -> {
                log.warn("FLINK_K8S_OPERATOR 运行阶段Operator状态未知, taskInstanceId={}, appId={},"
                                + " localState={}, operatorState={}",
                        state.getTaskInstanceId(), state.getAppId(), state.getStatus(), operatorState);
                yield StatusEnum.UNKNOWN;
            }
        };
    }

    /**
     * 映射停止阶段 Operator 状态.
     *
     * @param operatorState     Operator 状态
     * @param runtimePodsAbsent 运行 Pod 是否已不存在
     * @param state             任务执行状态
     * @return DataFusion 任务状态
     */
    private StatusEnum mapStoppingState(FlinkOperatorStatus.State operatorState, boolean runtimePodsAbsent,
            WorkerTaskExecutionState state) {
        return switch (operatorState) {
            case NONE -> runtimePodsAbsent ? StatusEnum.STOP_SUCCESS : StatusEnum.STOPPING;
            case FINISHED -> StatusEnum.RUN_SUCCESS;
            case FAILED -> StatusEnum.RUN_FAILURE;
            case CANCELED -> StatusEnum.STOP_SUCCESS;
            case SUSPENDED, CANCELLING, RECONCILING, RUNNING, RESTARTING, CREATED, INITIALIZING, FAILING ->
                    runtimePodsAbsent ? StatusEnum.STOP_SUCCESS : StatusEnum.STOPPING;
            case UNKNOWN -> {
                log.warn("FLINK_K8S_OPERATOR 停止阶段Operator状态未知, taskInstanceId={}, appId={},"
                                + " localState={}, operatorState={}, runtimePodsAbsent={}",
                        state.getTaskInstanceId(), state.getAppId(), state.getStatus(), operatorState,
                        runtimePodsAbsent);
                yield StatusEnum.UNKNOWN;
            }
        };
    }

    private String collectLogs(WorkerTaskExecutionState state, FlinkExecutionParam param,
            FlinkKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = operatorClient.collectLogs(runtimeRef);
            Path logFile = taskRuntimeDir(state, param).resolve("k8s-flink.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            log.warn("采集K8S_OPERATOR Flink日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
            return firstText(pluginLogUri(state), pluginLogUri(runtimeRef));
        }
    }

    private Path taskRuntimeDir(WorkerTaskExecutionState state, FlinkExecutionParam param) {
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
        request.setTaskData(snapshot.getTaskData());
        request.setPluginParam(resolvedPluginParam(snapshot));
        return request;
    }

    private JsonNode resolvedPluginParam(WorkerTaskExecutionSnap snapshot) {
        ObjectNode pluginParam = snapshot.getPluginParam() != null && snapshot.getPluginParam().isObject()
                ? (ObjectNode) snapshot.getPluginParam().deepCopy() : OBJECT_MAPPER.createObjectNode();
        if (!pluginParam.hasNonNull(FlinkParamResolver.FIELD_RUN_MODE) && snapshot.getRunMode() != null) {
            pluginParam.put(FlinkParamResolver.FIELD_RUN_MODE, snapshot.getRunMode());
        }
        return pluginParam;
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
