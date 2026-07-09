package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxJobFileService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fabric8 DataX Kubernetes 客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class Fabric8DataxKubernetesClient implements DataxKubernetesClient {

    /**
     * Kubernetes 客户端.
     */
    private final KubernetesClient client;

    /**
     * Job 文件服务.
     */
    private final DataxJobFileService jobFileService;

    /**
     * YAML 模板渲染器.
     */
    private final DataxKubernetesTemplateRenderer templateRenderer;

    /**
     * 完成条件类型.
     */
    private static final String COMPLETE_CONDITION = "Complete";

    /**
     * 失败条件类型.
     */
    private static final String FAILED_CONDITION = "Failed";

    /**
     * Pod 运行阶段.
     */
    private static final String RUNNING_PHASE = "Running";

    /**
     * Pod 等待阶段.
     */
    private static final String PENDING_PHASE = "Pending";

    /**
     * 清理等待超时时间.
     */
    private static final long CLEANUP_WAIT_TIMEOUT_MS = 30_000L;

    /**
     * 清理等待间隔.
     */
    private static final long CLEANUP_WAIT_INTERVAL_MS = 500L;

    /**
     * 构造函数.
     *
     * @param properties       Agent 配置
     * @param jobFileService   Job 文件服务
     * @param templateRenderer YAML 模板渲染器
     */
    public Fabric8DataxKubernetesClient(AgentProperties properties, DataxJobFileService jobFileService,
            DataxKubernetesTemplateRenderer templateRenderer) {
        this.jobFileService = jobFileService;
        this.templateRenderer = templateRenderer;
        Config config = config(properties.getKubernetes());
        this.client = config == null ? new KubernetesClientBuilder().build()
                : new KubernetesClientBuilder().withConfig(config).build();
    }

    @Override
    public DataxKubernetesRuntimeRef submit(DataxExecutionParam param) {
        DataxKubernetesParam kubernetes = param.getKubernetes();
        ensureImage(kubernetes);
        String manifest = templateRenderer.render(param, jobContent(param));
        client.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).createOrReplace();
        return DataxKubernetesRuntimeRef.builder()
                .namespace(kubernetes.getNamespace())
                .jobName(kubernetes.getJobName())
                .secretName(kubernetes.getSecretName())
                .podLabelSelector(kubernetes.getPodLabelSelector())
                .containerName(kubernetes.getContainerName())
                .logStorageUri(kubernetes.getLogStorageUri())
                .collectLogsOnFinish(kubernetes.isCollectLogsOnFinish())
                .deleteJobOnFinish(kubernetes.isDeleteJobOnFinish())
                .build();
    }

    @Override
    public void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
        try {
            deleteJob(runtimeRef, forcibly);
        } finally {
            deleteSecret(runtimeRef);
        }
    }

    @Override
    public DataxKubernetesStatus queryStatus(DataxKubernetesRuntimeRef runtimeRef) {
        Job job = job(runtimeRef);
        List<Pod> pods = pods(runtimeRef);
        boolean podRunning = pods.stream().anyMatch(this::podRunning);
        if (job == null) {
            return status(DataxKubernetesStatus.State.NONE, false, false, !pods.isEmpty(), podRunning);
        }
        if (job.getStatus() == null) {
            return status(DataxKubernetesStatus.State.NONE, true, false, !pods.isEmpty(), podRunning);
        }
        if (hasCondition(job, COMPLETE_CONDITION)) {
            return status(DataxKubernetesStatus.State.COMPLETE, true, true, !pods.isEmpty(), podRunning);
        }
        if (hasCondition(job, FAILED_CONDITION)) {
            return status(DataxKubernetesStatus.State.FAILED, true, true, !pods.isEmpty(), podRunning);
        }
        DataxKubernetesStatus.State state = isActive(job) ? DataxKubernetesStatus.State.ACTIVE
                : DataxKubernetesStatus.State.NONE;
        return status(state, true, true, !pods.isEmpty(), podRunning);
    }

    @Override
    public String collectLogs(DataxKubernetesRuntimeRef runtimeRef) {
        StringBuilder builder = new StringBuilder();
        for (Pod pod : pods(runtimeRef)) {
            String log = podLog(runtimeRef, pod);
            builder.append("===== pod: ").append(pod.getMetadata().getName()).append(" =====")
                    .append(System.lineSeparator())
                    .append(log == null ? "" : log)
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    @Override
    public boolean cleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode) {
        if (mode == DataxKubernetesCleanupMode.BEFORE_SUBMIT) {
            deleteJob(runtimeRef, true);
            deletePods(runtimeRef);
            deleteSecret(runtimeRef);
            return waitCleanup(runtimeRef, mode);
        }
        if (runtimeRef.isDeleteJobOnFinish()) {
            deleteJob(runtimeRef, false);
        }
        deleteSecret(runtimeRef);
        return waitCleanup(runtimeRef, mode);
    }

    private static Config config(AgentProperties.Kubernetes kubernetes) {
        if (!hasCustomConfig(kubernetes)) {
            return null;
        }
        ConfigBuilder builder = new ConfigBuilder();
        if (!isBlank(kubernetes.getApiServer())) {
            builder.withMasterUrl(kubernetes.getApiServer());
        }
        if (!isBlank(kubernetes.getToken())) {
            builder.withOauthToken(kubernetes.getToken());
        } else if (exists(kubernetes.getTokenFile())) {
            try {
                builder.withOauthToken(Files.readString(Path.of(kubernetes.getTokenFile())).trim());
            } catch (Exception e) {
                throw new IllegalStateException("读取Kubernetes token失败: " + kubernetes.getTokenFile(), e);
            }
        }
        if (exists(kubernetes.getCaCertFile())) {
            builder.withCaCertFile(kubernetes.getCaCertFile());
        }
        return builder.build();
    }

    private Job job(DataxKubernetesRuntimeRef runtimeRef) {
        return client.batch()
                .v1()
                .jobs()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getJobName())
                .get();
    }

    private List<Pod> pods(DataxKubernetesRuntimeRef runtimeRef) {
        return client.pods()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(DataxK8sNameGenerator.TASK_LABEL, labelValueFromSelector(runtimeRef.getPodLabelSelector()))
                .list()
                .getItems();
    }

    private boolean secretExists(DataxKubernetesRuntimeRef runtimeRef) {
        return client.secrets()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getSecretName())
                .get() != null;
    }

    private String podLog(DataxKubernetesRuntimeRef runtimeRef, Pod pod) {
        return client.pods()
                .inNamespace(runtimeRef.getNamespace())
                .withName(pod.getMetadata().getName())
                .inContainer(runtimeRef.getContainerName())
                .getLog();
    }

    private void deleteJob(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
        if (forcibly) {
            client.batch()
                    .v1()
                    .jobs()
                    .inNamespace(runtimeRef.getNamespace())
                    .withName(runtimeRef.getJobName())
                    .withGracePeriod(0L)
                    .delete();
            return;
        }
        client.batch()
                .v1()
                .jobs()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getJobName())
                .delete();
    }

    private void deletePods(DataxKubernetesRuntimeRef runtimeRef) {
        if (pods(runtimeRef).isEmpty()) {
            return;
        }
        client.pods()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(DataxK8sNameGenerator.TASK_LABEL, labelValueFromSelector(runtimeRef.getPodLabelSelector()))
                .withGracePeriod(0L)
                .delete();
    }

    private void deleteSecret(DataxKubernetesRuntimeRef runtimeRef) {
        client.secrets()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getSecretName())
                .delete();
    }

    private boolean waitCleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode) {
        long deadline = System.currentTimeMillis() + CLEANUP_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (cleanedUp(runtimeRef, mode)) {
                return true;
            }
            try {
                Thread.sleep(CLEANUP_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待DataX K8S旧资源清理被中断: " + runtimeRef.getJobName(), e);
            }
        }
        return false;
    }

    private boolean cleanedUp(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode) {
        if (mode == DataxKubernetesCleanupMode.AFTER_FINISH && !runtimeRef.isDeleteJobOnFinish()) {
            return !secretExists(runtimeRef);
        }
        return job(runtimeRef) == null && pods(runtimeRef).isEmpty() && !secretExists(runtimeRef);
    }

    private static boolean hasCustomConfig(AgentProperties.Kubernetes kubernetes) {
        return !isBlank(kubernetes.getApiServer()) || !isBlank(kubernetes.getToken())
                || exists(kubernetes.getTokenFile()) || exists(kubernetes.getCaCertFile());
    }

    private boolean hasCondition(Job job, String type) {
        List<JobCondition> conditions = job.getStatus().getConditions();
        if (conditions == null) {
            return false;
        }
        return conditions.stream().anyMatch(condition -> type.equals(condition.getType())
                && "True".equals(condition.getStatus()));
    }

    private boolean podRunning(Pod pod) {
        if (pod.getStatus() == null) {
            return false;
        }
        String phase = pod.getStatus().getPhase();
        return RUNNING_PHASE.equals(phase) || PENDING_PHASE.equals(phase);
    }

    private boolean isActive(Job job) {
        Integer active = job.getStatus().getActive();
        return active != null && active > 0;
    }

    private DataxKubernetesStatus status(DataxKubernetesStatus.State state, boolean jobExists, boolean jobStatusExists,
            boolean podExists, boolean podRunning) {
        return DataxKubernetesStatus.builder()
                .state(state)
                .jobExists(jobExists)
                .jobStatusExists(jobStatusExists)
                .podExists(podExists)
                .podRunning(podRunning)
                .build();
    }

    private void ensureImage(DataxKubernetesParam kubernetes) {
        if (isBlank(kubernetes.getImage())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.image或taskData.kubernetes.image不能为空");
        }
    }

    private String jobContent(DataxExecutionParam param) {
        try {
            return Files.readString(jobFileService.resolveJobFile(param), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取DataX job文件失败: " + e.getMessage(), e);
        }
    }

    private String labelValueFromSelector(String selector) {
        if (selector == null || !selector.contains("=")) {
            return "";
        }
        return selector.substring(selector.indexOf('=') + 1);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean exists(String path) {
        return !isBlank(path) && Files.exists(Path.of(path));
    }
}
