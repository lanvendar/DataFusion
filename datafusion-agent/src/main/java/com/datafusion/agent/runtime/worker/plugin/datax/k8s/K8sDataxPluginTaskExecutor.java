package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * K8S DataX 插件任务执行器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
@Slf4j
public class K8sDataxPluginTaskExecutor extends DataxPluginTaskExecutor {

    /**
     * Kubernetes 客户端.
     */
    private final DataxKubernetesClient kubernetesClient;

    /**
     * 构造函数.
     *
     * @param paramResolver 参数解析器
     * @param kubernetesClient Kubernetes 客户端
     */
    public K8sDataxPluginTaskExecutor(DataxParamResolver paramResolver, DataxKubernetesClient kubernetesClient) {
        super(paramResolver);
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public String runMode() {
        return DataxRunMode.K8S.name();
    }

    @Override
    protected WorkerResult submit(RunningTaskContext context, DataxExecutionParam param) {
        WorkerTaskExecutionState state = context.getExecutionState();
        try {
            if (!kubernetesClient.cleanup(runtimeRef(param, param.getKubernetes().getJobName()),
                    DataxKubernetesCleanupMode.BEFORE_SUBMIT)) {
                applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                        resultJson("K8S DataX cleanup before submit unfinished", null));
                return workerResult(state);
            }
        } catch (RuntimeException e) {
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                    resultJson("K8S DataX cleanup before submit failed: " + e.getMessage(), null));
            return workerResult(state);
        }
        try {
            DataxKubernetesRuntimeRef runtimeRef = kubernetesClient.submit(param);
            applyResult(state, StatusEnum.SUBMIT_SUCCESS, runtimeRef.getJobName(), param.getWorkDir().toString(),
                    resultJson("K8S DataX job submitted", pluginLogUri(runtimeRef)));
        } catch (Exception e) {
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                    PluginResultJson.build(e.getMessage(), "DATAX", DataxRunMode.K8S.name(), null, null));
        }
        return workerResult(state);
    }

    @Override
    protected WorkerResult stop(RunningTaskContext context, DataxExecutionParam param) {
        WorkerTaskExecutionState state = context.getExecutionState();
        DataxKubernetesRuntimeRef runtimeRef = runtimeRef(param, state.getAppId());
        kubernetesClient.stop(runtimeRef, false);
        applyControlResult(state, StatusEnum.STOPPING, "K8S DataX stop requested", context.getWorkDirPath());
        return workerResult(state);
    }

    @Override
    protected WorkerResult kill(RunningTaskContext context, DataxExecutionParam param) {
        WorkerTaskExecutionState state = context.getExecutionState();
        if (isBlank(state.getAppId())) {
            applyControlResult(state, StatusEnum.KILLED,
                    "K8S DataX runtime ref not found, nothing to kill", context.getWorkDirPath());
            return workerResult(state);
        }
        try {
            DataxKubernetesRuntimeRef runtimeRef = runtimeRef(param, state.getAppId());
            kubernetesClient.stop(runtimeRef, true);
            applyControlResult(state, StatusEnum.KILLING, "K8S DataX kill requested", context.getWorkDirPath());
        } catch (RuntimeException e) {
            log.warn("DataX K8S强杀失败, taskInstanceId={}, appId={}, error={}",
                    param.getTaskInstanceId(), state.getAppId(), e.getMessage());
            applyControlResult(state, StatusEnum.UNKNOWN,
                    "K8S DataX kill failed: " + e.getMessage(), context.getWorkDirPath());
        }
        return workerResult(state);
    }

    @Override
    protected boolean finish(RunningTaskContext context, DataxExecutionParam param) {
        WorkerTaskExecutionState state = context.getExecutionState();
        if (isBlank(state.getAppId())) {
            return true;
        }
        return kubernetesClient.cleanup(runtimeRef(param, state.getAppId()),
                DataxKubernetesCleanupMode.AFTER_FINISH);
    }

    private void applyControlResult(WorkerTaskExecutionState state, StatusEnum status, String message,
            String workDirPath) {
        applyResult(state, status, state.getAppId(), firstText(state.getWorkDirPath(), workDirPath),
                resultJson(message, pluginLogUri(state)));
    }

    private DataxKubernetesRuntimeRef runtimeRef(DataxExecutionParam param, String jobName) {
        if (isBlank(jobName)) {
            throw new IllegalArgumentException("K8S DataX runtime ref不存在");
        }
        DataxKubernetesParam kubernetes = param.getKubernetes();
        return DataxKubernetesRuntimeRef.builder()
                .namespace(kubernetes.getNamespace())
                .jobName(jobName)
                .secretName(kubernetes.getSecretName())
                .podLabelSelector(kubernetes.getPodLabelSelector())
                .containerName(kubernetes.getContainerName())
                .logStorageUri(kubernetes.getLogStorageUri())
                .collectLogsOnFinish(kubernetes.isCollectLogsOnFinish())
                .deleteJobOnFinish(kubernetes.isDeleteJobOnFinish())
                .build();
    }

    private String pluginLogUri(DataxKubernetesRuntimeRef runtimeRef) {
        if (runtimeRef.getLogStorageUri() != null && !runtimeRef.getLogStorageUri().trim().isEmpty()) {
            return runtimeRef.getLogStorageUri();
        }
        return "k8s://" + runtimeRef.getNamespace() + "/jobs/" + runtimeRef.getJobName();
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
    }

    private ObjectNode resultJson(String message, String pluginLogUri) {
        return PluginResultJson.build(message, "DATAX", DataxRunMode.K8S.name(), pluginLogUri, null);
    }

    private String firstText(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
