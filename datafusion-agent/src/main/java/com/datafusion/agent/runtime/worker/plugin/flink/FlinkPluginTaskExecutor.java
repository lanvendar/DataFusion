package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesRuntimeRef;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.K8sOperatorClient;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Flink 插件任务执行器.
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
     * 状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Operator 客户端.
     */
    private final K8sOperatorClient operatorClient;

    /**
     * 构造函数.
     *
     * @param paramResolver 参数解析器
     * @param stateStore 状态存储
     * @param operatorClient Operator 客户端
     */
    public FlinkPluginTaskExecutor(FlinkParamResolver paramResolver, WorkerTaskExecutionStore stateStore,
            K8sOperatorClient operatorClient) {
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
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
    public void validateTaskRequest(TaskRequest request) {
        paramResolver.resolve(request);
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        FlinkExecutionParam param = paramResolver.resolve(request);
        FlinkTaskResult result = submit(param);
        stateStore.saveSnapshot(snapshot(request));
        WorkerResult requestWorkerResult = request.getWorkerResult();
        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                .appId(result.getAppId())
                .workDirPath(result.getWorkDirPath())
                .status(result.getStatus())
                .result(result.getResult())
                .build();
        stateStore.saveState(state);
        return taskResult(request, result);
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        state.setStatus(StatusEnum.STOPPING);
        stateStore.saveState(state);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        FlinkExecutionParam param = paramResolver.resolve(resolvedRequest);
        FlinkTaskResult result = stop(param, state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        state.setStatus(StatusEnum.KILLING);
        stateStore.saveState(state);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        FlinkExecutionParam param = paramResolver.resolve(resolvedRequest);
        FlinkTaskResult result = kill(param, state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        if (resolvedRequest.getPluginParam() == null) {
            return true;
        }
        FlinkExecutionParam param = paramResolver.resolve(resolvedRequest);
        return finish(param, state);
    }

    private WorkerTaskExecutionState currentState(TaskRequest request) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return stateStore.readState(request.getTaskInstanceId())
                .orElseGet(() -> WorkerTaskExecutionState.builder()
                        .taskInstanceId(request.getTaskInstanceId())
                        .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                        .appId(requestWorkerResult == null ? null : requestWorkerResult.getAppId())
                        .status(request.getTaskState())
                        .build());
    }

    private TaskRequest resolveRequest(TaskRequest request, WorkerTaskExecutionState state) {
        if (request.getPluginParam() != null && request.getTaskData() != null) {
            return request;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(request.getTaskInstanceId()).orElse(null);
        if (snapshot == null && state != null) {
            snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        }
        if (snapshot == null) {
            return request;
        }
        TaskRequest resolvedRequest = new TaskRequest();
        resolvedRequest.setFlowInstanceId(firstText(request.getFlowInstanceId(), snapshot.getFlowInstanceId()));
        resolvedRequest.setTaskInstanceId(firstText(request.getTaskInstanceId(), snapshot.getTaskInstanceId()));
        resolvedRequest.setTaskName(firstText(request.getTaskName(), snapshot.getTaskName()));
        resolvedRequest.setPluginType(firstText(request.getPluginType(), snapshot.getPluginType()));
        resolvedRequest.setRunMode(firstText(request.getRunMode(), snapshot.getRunMode()));
        resolvedRequest.setWorkerResult(request.getWorkerResult());
        resolvedRequest.setTaskState(request.getTaskState());
        resolvedRequest.setSubmitMode(request.getSubmitMode());
        resolvedRequest.setTaskData(request.getTaskData() == null ? snapshot.getTaskData() : request.getTaskData());
        resolvedRequest.setPluginParam(request.getPluginParam() == null
                ? snapshot.getPluginParam() : request.getPluginParam());
        return resolvedRequest;
    }

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState state, FlinkTaskResult result) {
        WorkerTaskExecutionState next = state == null ? currentState(request) : state;
        WorkerResult requestWorkerResult = request.getWorkerResult();
        next.setStatus(result.getStatus());
        next.setWorkerId(firstText(next.getWorkerId(), requestWorkerResult == null ? null : requestWorkerResult.getWorkerId()));
        next.setAppId(result.getAppId() == null ? next.getAppId() : result.getAppId());
        next.setWorkDirPath(result.getWorkDirPath() == null ? next.getWorkDirPath() : result.getWorkDirPath());
        next.setResult(result.getResult());
        stateStore.saveState(next);
    }

    private String resultText(JsonNode result, String fieldName) {
        if (result == null || !result.hasNonNull(fieldName)) {
            return null;
        }
        return result.get(fieldName).asText();
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                .pluginType(PLUGIN_TYPE)
                .runMode(runMode())
                .taskInstanceId(request.getTaskInstanceId())
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private TaskResult taskResult(TaskRequest request, FlinkTaskResult result) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(result.getStatus())
                .workerResult(workerResult(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                        result.getAppId(), result.getWorkDirPath(), result.getResult()))
                .build();
    }

    private WorkerResult workerResult(String workerId, String appId, String workDirPath, JsonNode result) {
        return WorkerResult.builder()
                .workerId(workerId)
                .appId(appId)
                .workDirPath(workDirPath)
                .message(resultText(result, "message"))
                .pluginLogUri(resultText(result, "pluginLogUri"))
                .build();
    }

    private FlinkTaskResult submit(FlinkExecutionParam param) {
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
                    .result(resultJson("K8S_OPERATOR Flink job submitted", runtimeRef, pluginLogUri(runtimeRef)))
                    .build();
        } catch (Exception e) {
            log.warn("K8S_OPERATOR Flink任务提交失败, taskInstanceId={}, flowInstanceId={}, workDirPath={}",
                    param.getTaskInstanceId(), param.getFlowInstanceId(), param.getWorkDir(), e);
            return FlinkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .workDirPath(param.getWorkDir().toString())
                    .result(PluginResultJson.build(e.getMessage(), PLUGIN_TYPE, runMode(), null, null))
                    .build();
        }
    }

    private FlinkTaskResult stop(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        try {
            FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            log.info("K8S_OPERATOR Flink任务请求停止, taskInstanceId={}, namespace={}, deploymentName={}",
                    param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
            operatorClient.stop(runtimeRef);
            return controlResult(param, state, StatusEnum.STOPPING, "K8S_OPERATOR Flink stop requested");
        } catch (RuntimeException e) {
            log.warn("K8S_OPERATOR Flink任务停止失败, taskInstanceId={}, appId={}",
                    param.getTaskInstanceId(), state == null ? null : state.getAppId(), e);
            return controlResult(param, state, StatusEnum.STOP_FAILURE,
                    "K8S_OPERATOR Flink stop failed: " + e.getMessage());
        }
    }

    private FlinkTaskResult kill(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return controlResult(param, state, StatusEnum.KILLED,
                    "K8S_OPERATOR Flink runtime ref not found, nothing to kill");
        }
        try {
            FlinkKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
            log.info("K8S_OPERATOR Flink任务请求强杀, taskInstanceId={}, namespace={}, deploymentName={}",
                    param.getTaskInstanceId(), runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
            operatorClient.kill(runtimeRef);
            return controlResult(param, state, StatusEnum.KILLED, "K8S_OPERATOR Flink kill completed");
        } catch (RuntimeException e) {
            log.warn("K8S_OPERATOR Flink任务强杀失败, taskInstanceId={}, appId={}",
                    param.getTaskInstanceId(), state.getAppId(), e);
            return controlResult(param, state, StatusEnum.UNKNOWN,
                    "K8S_OPERATOR Flink kill failed: " + e.getMessage());
        }
    }

    private boolean finish(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        return state == null || isBlank(state.getAppId()) || operatorClient.cleanup(runtimeRef(param, state));
    }

    private FlinkTaskResult controlResult(FlinkExecutionParam param, WorkerTaskExecutionState state,
            StatusEnum status, String message) {
        FlinkKubernetesRuntimeRef runtimeRef = state == null || isBlank(state.getAppId())
                ? null : runtimeRef(param, state);
        String pluginLogUri = firstText(pluginLogUri(state),
                runtimeRef == null ? null : pluginLogUri(runtimeRef));
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

    private ObjectNode resultJson(String message, FlinkKubernetesRuntimeRef runtimeRef, String pluginLogUri) {
        ObjectNode result = PluginResultJson.build(message, PLUGIN_TYPE, runMode(), pluginLogUri, null);
        if (runtimeRef != null) {
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
        JsonNode result = state == null ? null : state.getResult();
        return result != null && result.hasNonNull("pluginLogUri") ? result.get("pluginLogUri").asText() : null;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
