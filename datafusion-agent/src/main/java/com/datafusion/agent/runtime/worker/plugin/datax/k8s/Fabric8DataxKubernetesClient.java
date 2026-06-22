package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxJobFileService;
import com.datafusion.scheduler.enums.StatusEnum;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fabric8 DataX Kubernetes client.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class Fabric8DataxKubernetesClient implements DataxKubernetesClient {

    /**
     * Kubernetes client.
     */
    private final KubernetesClient client;

    /**
     * Job file service.
     */
    private final DataxJobFileService jobFileService;

    /**
     * YAML template renderer.
     */
    private final DataxKubernetesTemplateRenderer templateRenderer;

    /**
     * Complete condition type.
     */
    private static final String COMPLETE_CONDITION = "Complete";

    /**
     * Failed condition type.
     */
    private static final String FAILED_CONDITION = "Failed";

    /**
     * Running pod phase.
     */
    private static final String RUNNING_PHASE = "Running";

    /**
     * Pending pod phase.
     */
    private static final String PENDING_PHASE = "Pending";

    /**
     * Constructor.
     *
     * @param properties       agent properties
     * @param jobFileService   job file service
     * @param templateRenderer YAML template renderer
     */
    public Fabric8DataxKubernetesClient(AgentProperties properties, DataxJobFileService jobFileService,
            DataxKubernetesTemplateRenderer templateRenderer) {
        this.jobFileService = jobFileService;
        this.templateRenderer = templateRenderer;
        Config config = config(properties.getKubernetes());
        this.client = config == null ? new DefaultKubernetesClient() : new DefaultKubernetesClient(config);
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
    public StatusEnum queryStatus(DataxKubernetesRuntimeRef runtimeRef, StatusEnum localState) {
        if (localState != null && localState.isFinalState()) {
            return localState;
        }
        if (isTerminatingState(localState)) {
            return podsRunning(runtimeRef) ? localState : terminalControlState(localState);
        }
        Job job = job(runtimeRef);
        if (job == null || job.getStatus() == null) {
            return StatusEnum.UNKNOWN;
        }
        if (hasCondition(job, COMPLETE_CONDITION)) {
            return StatusEnum.RUN_SUCCESS;
        }
        if (hasCondition(job, FAILED_CONDITION)) {
            return StatusEnum.RUN_FAILURE;
        }
        return isActive(job) ? StatusEnum.RUNNING : StatusEnum.UNKNOWN;
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
    public void cleanup(DataxKubernetesRuntimeRef runtimeRef) {
        try {
            if (runtimeRef.isDeleteJobOnFinish()) {
                deleteJob(runtimeRef, false);
            }
        } finally {
            deleteSecret(runtimeRef);
        }
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

    private void deleteSecret(DataxKubernetesRuntimeRef runtimeRef) {
        client.secrets()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getSecretName())
                .delete();
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

    private boolean podsRunning(DataxKubernetesRuntimeRef runtimeRef) {
        return pods(runtimeRef).stream().anyMatch(this::podRunning);
    }

    private boolean podRunning(Pod pod) {
        if (pod.getStatus() == null) {
            return false;
        }
        String phase = pod.getStatus().getPhase();
        return RUNNING_PHASE.equals(phase) || PENDING_PHASE.equals(phase);
    }

    private boolean isTerminatingState(StatusEnum localState) {
        return localState == StatusEnum.STOPPING || localState == StatusEnum.KILLING;
    }

    private StatusEnum terminalControlState(StatusEnum localState) {
        return localState == StatusEnum.STOPPING ? StatusEnum.STOP_SUCCESS : StatusEnum.KILLED;
    }

    private boolean isActive(Job job) {
        Integer active = job.getStatus().getActive();
        return active != null && active > 0;
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
