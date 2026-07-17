package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
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
 * DataX K8S 运行模式状态映射.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Slf4j
@Component
public class DataxK8sRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Kubernetes 客户端.
     */
    private final DataxKubernetesClient kubernetesClient;

    /**
     * 参数解析器.
     */
    private final DataxParamResolver paramResolver;

    /**
     * 构造函数.
     *
     * @param kubernetesClient Kubernetes 客户端
     * @param paramResolver    参数解析器
     */
    public DataxK8sRunModeStateMapping(DataxKubernetesClient kubernetesClient, DataxParamResolver paramResolver) {
        this.kubernetesClient = kubernetesClient;
        this.paramResolver = paramResolver;
    }

    @Override
    public String pluginType() {
        return DataxPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return DataxRunMode.K8S.name();
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        DataxExecutionParam param = paramResolver.resolve(snapshot, state.getWorkDirPath());
        if (isBlank(state.getAppId())) {
            state.setAppId(param.getKubernetes().getJobName());
        }
        return mapKubernetesStatus(kubernetesClient.queryStatus(runtimeRef(param, state)), state);
    }

    @Override
    public boolean prepareFinalReport(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        if (isFinalized(state)) {
            return false;
        }
        DataxExecutionParam param = paramResolver.resolve(snapshot, state.getWorkDirPath());
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(param, state);
        String pluginLogUri = collectLogs(state, param, runtimeRef);
        ObjectNode result = PluginResultJson.build("K8S DataX task finished", pluginType(), runMode(), pluginLogUri, null);
        result.put("finalized", true);
        state.setResult(result);
        return true;
    }

    private DataxKubernetesRuntimeRef runtimeRef(DataxExecutionParam param, WorkerTaskExecutionState state) {
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

    /**
     * 映射 Kubernetes 状态.
     *
     * @param kubernetesStatus Kubernetes 状态快照
     * @param state            任务执行状态
     * @return DataFusion 任务状态
     */
    private StatusEnum mapKubernetesStatus(DataxKubernetesStatus kubernetesStatus, WorkerTaskExecutionState state) {
        StatusEnum localState = state.getStatus();
        if (kubernetesStatus == null || kubernetesStatus.getState() == null) {
            log.warn("DataX K8S的K8S状态为空, taskInstanceId={}, appId={}",
                    state.getTaskInstanceId(), state.getAppId());
            return StatusEnum.UNKNOWN;
        }
        if (localState == StatusEnum.STOPPING) {
            return kubernetesStatus.isPodRunning() ? StatusEnum.STOPPING : StatusEnum.STOP_SUCCESS;
        }
        if (localState == StatusEnum.KILLING) {
            return kubernetesStatus.isJobExists() || kubernetesStatus.isPodExists()
                    ? StatusEnum.KILLING : StatusEnum.KILLED;
        }
        if (!kubernetesStatus.isJobExists()) {
            log.warn("DataX K8S的Job不存在, taskInstanceId={}, appId={}, localState={}",
                    state.getTaskInstanceId(), state.getAppId(), localState);
            return localState == StatusEnum.SUBMITTING ? StatusEnum.SUBMIT_FAILURE : StatusEnum.UNKNOWN;
        }
        if (!kubernetesStatus.isJobStatusExists()) {
            log.warn("DataX K8S的Job状态为空, taskInstanceId={}, appId={}, localState={}",
                    state.getTaskInstanceId(), state.getAppId(), localState);
            return StatusEnum.UNKNOWN;
        }
        return switch (kubernetesStatus.getState()) {
            case COMPLETE -> StatusEnum.RUN_SUCCESS;
            case FAILED -> StatusEnum.RUN_FAILURE;
            case ACTIVE -> StatusEnum.RUNNING;
            case NONE -> {
                log.warn("DataX K8S的Job未激活, taskInstanceId={}, appId={}, localState={}",
                        state.getTaskInstanceId(), state.getAppId(), localState);
                yield StatusEnum.UNKNOWN;
            }
        };
    }

    private String collectLogs(WorkerTaskExecutionState state, DataxExecutionParam param,
            DataxKubernetesRuntimeRef runtimeRef) {
        if (!isBlank(runtimeRef.getLogStorageUri())) {
            return runtimeRef.getLogStorageUri();
        }
        if (!runtimeRef.isCollectLogsOnFinish()) {
            return pluginLogUri(runtimeRef);
        }
        try {
            String logs = kubernetesClient.collectLogs(runtimeRef);
            Path logFile = taskRuntimeDir(state, param).resolve("k8s-datax.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, logs, StandardCharsets.UTF_8);
            return logFile.toString();
        } catch (Exception e) {
            log.warn("采集K8S DataX日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
            return firstText(pluginLogUri(state), pluginLogUri(runtimeRef));
        }
    }

    private Path taskRuntimeDir(WorkerTaskExecutionState state, DataxExecutionParam param) {
        if (!isBlank(state.getWorkDirPath())) {
            return Path.of(state.getWorkDirPath());
        }
        return param.getWorkDir();
    }

    private String pluginLogUri(DataxKubernetesRuntimeRef runtimeRef) {
        return "k8s://" + runtimeRef.getNamespace() + "/jobs/" + runtimeRef.getJobName();
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
