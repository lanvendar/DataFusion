package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxSubmitResult;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxTaskRunner;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * K8S DataX task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class K8sDataxTaskRunner implements DataxTaskRunner {

    /**
     * Kubernetes client.
     */
    private final DataxKubernetesClient kubernetesClient;

    /**
     * Parameter resolver.
     */
    private final DataxParamResolver paramResolver;

    /**
     * Agent properties.
     */
    private final AgentProperties properties;

    /**
     * State store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Constructor.
     *
     * @param kubernetesClient Kubernetes client
     * @param properties       agent properties
     * @param paramResolver    parameter resolver
     * @param stateStore       state store
     */
    public K8sDataxTaskRunner(DataxKubernetesClient kubernetesClient, AgentProperties properties,
            DataxParamResolver paramResolver, WorkerTaskExecutionStore stateStore) {
        this.kubernetesClient = kubernetesClient;
        this.properties = properties;
        this.paramResolver = paramResolver;
        this.stateStore = stateStore;
    }

    @Override
    public DataxRunMode runMode() {
        return DataxRunMode.K8S;
    }

    @Override
    public DataxSubmitResult submit(TaskRequest request, DataxExecutionParam param) {
        try {
            DataxKubernetesRuntimeRef runtimeRef = kubernetesClient.submit(param);
            return DataxSubmitResult.builder()
                    .status(StatusEnum.RUNNING)
                    .appId(runtimeRef.getJobName())
                    .workDirPath(param.getWorkDir().toString())
                    .result(resultJson("K8S DataX job submitted", pluginLogUri(runtimeRef)))
                    .kubernetesRuntimeRef(runtimeRef)
                    .build();
        } catch (Exception e) {
            return DataxSubmitResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .result(PluginResultJson.build(e.getMessage(), "DATAX", DataxRunMode.K8S.name(), null, null))
                    .build();
        }
    }

    @Override
    public TaskResult stop(TaskRequest request, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        kubernetesClient.stop(runtimeRef, false);
        return result(request, state, StatusEnum.STOPPING, "K8S DataX stop requested", null);
    }

    @Override
    public TaskResult kill(TaskRequest request, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        kubernetesClient.stop(runtimeRef, true);
        return result(request, state, StatusEnum.KILLING, "K8S DataX kill requested", null);
    }

    @Override
    public TaskResult finish(TaskRequest request, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        StatusEnum status = kubernetesClient.queryStatus(runtimeRef, state == null ? null : state.getStatus());
        if (!status.isFinalState()) {
            return result(request, state, status, "K8S DataX task is not finished", null);
        }
        String collectedLogPath = collectLogsIfNecessary(request, state, runtimeRef);
        kubernetesClient.cleanup(runtimeRef);
        return result(request, state, status, "K8S DataX finish checked", collectedLogPath);
    }

    private String collectLogsIfNecessary(TaskRequest request, WorkerTaskExecutionState state,
            DataxKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return pluginLogUri(runtimeRef);
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = kubernetesClient.collectLogs(runtimeRef);
            TaskRequest resolvedRequest = resolveRequest(request);
            Path taskRuntimeDir = taskRuntimeDir(resolvedRequest, state);
            Files.createDirectories(taskRuntimeDir);
            Path logFile = taskRuntimeDir.resolve("k8s-datax.log");
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            return pluginLogUri(runtimeRef);
        }
    }

    private TaskResult result(TaskRequest request, WorkerTaskExecutionState state, StatusEnum status, String message,
            String pluginLogPath) {
        TaskRequest resolvedRequest = resolveRequest(request);
        DataxKubernetesRuntimeRef runtimeRef = state == null ? null : runtimeRef(request, state);
        WorkerResult requestWorkerResult = resolvedRequest.getWorkerResult();
        String pluginLogUri = firstText(pluginLogPath, firstText(pluginLogUri(state),
                runtimeRef == null ? null : pluginLogUri(runtimeRef)));
        String appId = null;
        if (state != null) {
            appId = state.getAppId();
        } else if (requestWorkerResult != null) {
            appId = requestWorkerResult.getAppId();
        }
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
                        .pluginLogUri(firstText(pluginLogUri, runtimeRef == null ? null : pluginLogUri(runtimeRef)))
                        .build())
                .build();
    }

    private DataxKubernetesRuntimeRef runtimeRef(TaskRequest request, WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            throw new IllegalArgumentException("K8S DataX runtime ref不存在");
        }
        DataxExecutionParam param = paramResolver.resolve(resolveRequest(request));
        return DataxKubernetesRuntimeRef.builder()
                .namespace(param.getKubernetes().getNamespace())
                .jobName(state.getAppId())
                .secretName(param.getKubernetes().getSecretName())
                .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                .containerName(param.getKubernetes().getContainerName())
                .logStorageUri(param.getKubernetes().getLogStorageUri())
                .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                .deleteJobOnFinish(param.getKubernetes().isDeleteJobOnFinish())
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

    private String pluginLogUri(DataxKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return runtimeRef.getLogStorageUri();
        }
        return "k8s://" + runtimeRef.getNamespace() + "/jobs/" + runtimeRef.getJobName();
    }

    private Path taskRuntimeDir(TaskRequest request, WorkerTaskExecutionState state) {
        if (state != null && firstText(state.getWorkDirPath(), null) != null) {
            return Path.of(state.getWorkDirPath());
        }
        return Path.of(properties.getStorage().getTaskRuntimeDir(),
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                firstText(request.getFlowInstanceId(), "unknown"), firstText(request.getTaskInstanceId(), "unknown"));
    }

    private ObjectNode resultJson(String message, String pluginLogUri) {
        return PluginResultJson.build(message, "DATAX", DataxRunMode.K8S.name(), pluginLogUri, null);
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

}
