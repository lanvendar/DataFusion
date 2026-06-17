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
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
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
     * Constructor.
     *
     * @param kubernetesClient Kubernetes client
     * @param properties       agent properties
     * @param paramResolver    parameter resolver
     */
    public K8sDataxTaskRunner(DataxKubernetesClient kubernetesClient, AgentProperties properties,
            DataxParamResolver paramResolver) {
        this.kubernetesClient = kubernetesClient;
        this.properties = properties;
        this.paramResolver = paramResolver;
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
                    .logPath(param.getLogDir().toString())
                    .result(resultJson("K8S DataX job submitted", runtimeRef, param.getLogDir().toString()))
                    .kubernetesRuntimeRef(runtimeRef)
                    .build();
        } catch (Exception e) {
            return DataxSubmitResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .result(PluginResultJson.build(e.getMessage(), "DATAX", DataxRunMode.K8S.name(),
                            null, null, null))
                    .build();
        }
    }

    @Override
    public TaskResult stop(TaskRequest request, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        kubernetesClient.stop(runtimeRef, false);
        return result(request, state, StatusEnum.STOPPING, "K8S DataX stop requested");
    }

    @Override
    public TaskResult kill(TaskRequest request, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        kubernetesClient.stop(runtimeRef, true);
        return result(request, state, StatusEnum.KILLING, "K8S DataX kill requested");
    }

    @Override
    public TaskResult finish(TaskRequest request, WorkerTaskExecutionState state) {
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(request, state);
        StatusEnum status = kubernetesClient.queryStatus(runtimeRef, state == null ? null : state.getStatus());
        if (!status.isFinalState()) {
            return result(request, state, status, "K8S DataX task is not finished");
        }
        String collectedLogPath = collectLogsIfNecessary(request, runtimeRef);
        kubernetesClient.cleanup(runtimeRef);
        return result(request, state, status, "K8S DataX finish checked", collectedLogPath);
    }

    private String collectLogsIfNecessary(TaskRequest request, DataxKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return null;
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return null;
        }
        try {
            String logs = kubernetesClient.collectLogs(runtimeRef);
            Path logDir = logDir(request);
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("datax-k8s.log");
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private TaskResult result(TaskRequest request, WorkerTaskExecutionState state, StatusEnum status, String message) {
        return result(request, state, status, message, null);
    }

    private TaskResult result(TaskRequest request, WorkerTaskExecutionState state, StatusEnum status, String message,
            String collectedLogPath) {
        String agentLogPath = firstText(collectedLogPath, state == null ? null : state.getLogPath());
        DataxKubernetesRuntimeRef runtimeRef = state == null ? null : runtimeRef(request, state);
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(status)
                .appId(state == null ? request.getAppId() : state.getAppId())
                .logPath(agentLogPath)
                .result(resultJson(message, runtimeRef, agentLogPath))
                .build();
    }

    private DataxKubernetesRuntimeRef runtimeRef(TaskRequest request, WorkerTaskExecutionState state) {
        if (state == null || state.getAppId() == null) {
            throw new IllegalArgumentException("K8S DataX runtime ref不存在");
        }
        DataxExecutionParam param = paramResolver.resolve(request);
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

    private String pluginLogUri(DataxKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return runtimeRef.getLogStorageUri();
        }
        return "k8s://" + runtimeRef.getNamespace() + "/jobs/" + runtimeRef.getJobName();
    }

    private Path logDir(TaskRequest request) {
        return Path.of(properties.getModules(), properties.getStorage().getLogsDir(),
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                safePath(request.getFlowInstanceId()), safePath(request.getTaskInstanceId()));
    }

    private String safePath(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private ObjectNode resultJson(String message, DataxKubernetesRuntimeRef runtimeRef, String agentLogPath) {
        return PluginResultJson.build(message, "DATAX", DataxRunMode.K8S.name(),
                runtimeRef == null ? null : pluginLogUri(runtimeRef), agentLogPath, null);
    }

    private String firstText(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }
}
