package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.K8sOperatorClient;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesParam;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesRuntimeRef;
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
 * Spark 插件任务执行器.
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
     * @param stateStore    状态存储
     * @param operatorClient Operator 客户端
     */
    public SparkPluginTaskExecutor(SparkParamResolver paramResolver, WorkerTaskExecutionStore stateStore,
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
        return SparkRunMode.K8S_OPERATOR.name();
    }

    @Override
    public void validateTaskRequest(TaskRequest request) {
        paramResolver.resolve(request);
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        SparkExecutionParam param = paramResolver.resolve(request);
        SparkTaskResult result = submit(param);
        WorkerTaskExecutionState state = currentState(request);
        state.setAppId(result.getAppId());
        state.setWorkDirPath(result.getWorkDirPath());
        state.setStatus(result.getStatus());
        state.setResult(result.getResult());
        stateStore.saveState(state, state.getRevision());
        return taskResult(request, result);
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        SparkExecutionParam param = paramResolver.resolve(resolvedRequest);
        SparkTaskResult result = stop(param, state);
        recordControlResult(resolvedRequest, state, result);
        return taskResult(resolvedRequest, result);
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request, state);
        SparkExecutionParam param = paramResolver.resolve(resolvedRequest);
        SparkTaskResult result = kill(param, state);
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
        SparkExecutionParam param = paramResolver.resolve(resolvedRequest);
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

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState state, SparkTaskResult result) {
        WorkerTaskExecutionState next = state == null ? currentState(request) : state;
        WorkerResult requestWorkerResult = request.getWorkerResult();
        next.setStatus(result.getStatus());
        next.setWorkerId(firstText(next.getWorkerId(), requestWorkerResult == null ? null : requestWorkerResult.getWorkerId()));
        next.setAppId(result.getAppId() == null ? next.getAppId() : result.getAppId());
        next.setWorkDirPath(result.getWorkDirPath() == null ? next.getWorkDirPath() : result.getWorkDirPath());
        next.setResult(result.getResult());
        stateStore.saveState(next, next.getRevision());
    }

    private TaskResult taskResult(TaskRequest request, SparkTaskResult result) {
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

    private String resultText(JsonNode result, String fieldName) {
        if (result == null || !result.hasNonNull(fieldName)) {
            return null;
        }
        return result.get(fieldName).asText();
    }

    private SparkTaskResult submit(SparkExecutionParam param) {
        WorkerTaskExecutionState oldState = stateStore.readState(param.getTaskInstanceId()).orElse(null);
        if (oldState != null && !isBlank(oldState.getAppId())
                && !operatorClient.cleanup(runtimeRef(param, oldState))) {
            return controlResult(oldState, StatusEnum.SUBMIT_FAILURE,
                    "SPARK_K8S_OPERATOR cleanup before submit failed");
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
                    .result(resultJson("SPARK_K8S_OPERATOR SparkApplication submitted", runtimeRef,
                            pluginLogUri(runtimeRef)))
                    .build();
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR任务提交失败, taskInstanceId={}, error={}",
                    param.getTaskInstanceId(), e.getMessage());
            return SparkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .workDirPath(param.getWorkDir().toString())
                    .result(PluginResultJson.build(e.getMessage(), PLUGIN_TYPE, runMode(), null, null))
                    .build();
        }
    }

    private SparkTaskResult stop(SparkExecutionParam param, WorkerTaskExecutionState state) {
        operatorClient.stop(runtimeRef(param, state));
        return controlResult(state, StatusEnum.STOPPING, "SPARK_K8S_OPERATOR stop requested");
    }

    private SparkTaskResult kill(SparkExecutionParam param, WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getAppId())) {
            return controlResult(state, StatusEnum.KILLED,
                    "SPARK_K8S_OPERATOR runtime ref not found, nothing to kill");
        }
        try {
            operatorClient.kill(runtimeRef(param, state));
            return controlResult(state, StatusEnum.KILLING, "SPARK_K8S_OPERATOR kill requested");
        } catch (RuntimeException e) {
            log.warn("SPARK_K8S_OPERATOR强杀失败, taskInstanceId={}, appId={}, error={}",
                    param.getTaskInstanceId(), state.getAppId(), e.getMessage());
            return controlResult(state, StatusEnum.UNKNOWN,
                    "SPARK_K8S_OPERATOR kill failed: " + e.getMessage());
        }
    }

    private boolean finish(SparkExecutionParam param, WorkerTaskExecutionState state) {
        return state == null || isBlank(state.getAppId()) || operatorClient.cleanup(runtimeRef(param, state));
    }

    private SparkTaskResult controlResult(WorkerTaskExecutionState state, StatusEnum status, String message) {
        String pluginLogUri = pluginLogUri(state);
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

    private ObjectNode resultJson(String message, SparkKubernetesRuntimeRef runtimeRef, String pluginLogUri) {
        ObjectNode result = PluginResultJson.build(message, PLUGIN_TYPE, runMode(), pluginLogUri, null);
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
