package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric8 Flink Kubernetes Operator client.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Component
public class K8sOperatorFabric8Client implements K8sOperatorClient {

    /**
     * FlinkDeployment api version.
     */
    private static final String FLINK_DEPLOYMENT_API_VERSION = "flink.apache.org/v1beta1";

    /**
     * FlinkDeployment kind.
     */
    private static final String FLINK_DEPLOYMENT_KIND = "FlinkDeployment";

    /**
     * Job status path.
     */
    private static final List<String> JOB_STATUS_PATH = List.of("status", "jobStatus", "state");

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Kubernetes client.
     */
    private final KubernetesClient client;

    /**
     * YAML template renderer.
     */
    private final FlinkKubernetesTemplateRenderer templateRenderer;

    /**
     * Constructor.
     *
     * @param properties       agent properties
     * @param templateRenderer YAML template renderer
     */
    public K8sOperatorFabric8Client(AgentProperties properties, FlinkKubernetesTemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        Config config = config(properties.getKubernetes());
        this.client = config == null ? new KubernetesClientBuilder().build()
                : new KubernetesClientBuilder().withConfig(config).build();
    }

    @Override
    public FlinkKubernetesRuntimeRef submit(FlinkExecutionParam param) {
        validateSharedPluginFiles(param);
        String jobContent = writeJobJson(param);
        String manifest = templateRenderer.render(param, jobContent);
        client.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).createOrReplace();
        FlinkKubernetesParam kubernetes = param.getKubernetes();
        return FlinkKubernetesRuntimeRef.builder()
                .namespace(kubernetes.getNamespace())
                .deploymentName(kubernetes.getDeploymentName())
                .secretName(kubernetes.getSecretName())
                .podLabelSelector(kubernetes.getPodLabelSelector())
                .logStorageUri(kubernetes.getLogStorageUri())
                .flinkWebUiUri(kubernetes.getFlinkWebUiUri())
                .collectLogsOnFinish(kubernetes.isCollectLogsOnFinish())
                .deleteDeploymentOnFinish(kubernetes.isDeleteDeploymentOnFinish())
                .deleteSecretOnFinish(kubernetes.isDeleteSecretOnFinish())
                .build();
    }

    @Override
    public void stop(FlinkKubernetesRuntimeRef runtimeRef) {
        GenericKubernetesResource deployment = deployment(runtimeRef);
        if (deployment == null) {
            return;
        }
        Map<String, Object> spec = objectMap(deployment.getAdditionalProperties(), "spec");
        Map<String, Object> job = objectMap(spec, "job");
        job.put("state", "SUSPENDED");
        spec.put("job", job);
        deployment.setAdditionalProperty("spec", spec);
        deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).replace(deployment);
    }

    @Override
    public void kill(FlinkKubernetesRuntimeRef runtimeRef) {
        try {
            deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).withGracePeriod(0L).delete();
        } finally {
            deleteSecret(runtimeRef);
        }
    }

    @Override
    public StatusEnum queryStatus(FlinkKubernetesRuntimeRef runtimeRef, StatusEnum localState) {
        if (localState != null && localState.isFinalState()) {
            return localState;
        }
        GenericKubernetesResource deployment = deployment(runtimeRef);
        if (localState == StatusEnum.KILLING && deployment == null && pods(runtimeRef).isEmpty()) {
            return StatusEnum.KILLED;
        }
        if (deployment == null) {
            return StatusEnum.UNKNOWN;
        }
        String state = stringAt(deployment, JOB_STATUS_PATH);
        return mapOperatorState(state, localState);
    }

    @Override
    public String collectLogs(FlinkKubernetesRuntimeRef runtimeRef) {
        StringBuilder builder = new StringBuilder();
        for (Pod pod : pods(runtimeRef)) {
            String log = client.pods()
                    .inNamespace(runtimeRef.getNamespace())
                    .withName(pod.getMetadata().getName())
                    .getLog();
            builder.append("===== pod: ").append(pod.getMetadata().getName()).append(" =====")
                    .append(System.lineSeparator())
                    .append(log == null ? "" : log)
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    @Override
    public void cleanup(FlinkKubernetesRuntimeRef runtimeRef) {
        try {
            if (runtimeRef.isDeleteDeploymentOnFinish()) {
                deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).delete();
            }
        } finally {
            if (runtimeRef.isDeleteSecretOnFinish()) {
                deleteSecret(runtimeRef);
            }
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

    private void validateSharedPluginFiles(FlinkExecutionParam param) {
        Path appDir = Path.of(param.getKubernetes().getFlinkAppDir());
        if (!Files.exists(appDir)) {
            throw new IllegalArgumentException("共享盘Flink app目录不存在: " + appDir);
        }
        Path mainJar = appDir.resolve(param.getFlinkAppJar());
        if (!Files.isRegularFile(mainJar)) {
            throw new IllegalArgumentException("共享盘Flink app主jar不存在: " + mainJar);
        }
        Path libDir = appDir.resolve(param.getLibDir());
        if (!Files.exists(libDir)) {
            throw new IllegalArgumentException("共享盘Flink app依赖目录不存在: " + libDir);
        }
        if (!Files.isDirectory(libDir)) {
            throw new IllegalArgumentException("共享盘Flink app依赖路径不是目录: " + libDir);
        }
    }

    private String writeJobJson(FlinkExecutionParam param) {
        try {
            Files.createDirectories(param.getWorkDir());
            String content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(param.getEffectiveTaskData());
            Files.writeString(param.getWorkDir().resolve("job.json"), content, StandardCharsets.UTF_8);
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("写入Flink job.json失败: " + e.getMessage(), e);
        }
    }

    private GenericKubernetesResource deployment(FlinkKubernetesRuntimeRef runtimeRef) {
        return deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).get();
    }

    private io.fabric8.kubernetes.client.dsl.NonNamespaceOperation<GenericKubernetesResource,
            io.fabric8.kubernetes.api.model.GenericKubernetesResourceList,
            io.fabric8.kubernetes.client.dsl.Resource<GenericKubernetesResource>> deployments(
            FlinkKubernetesRuntimeRef runtimeRef) {
        return client.genericKubernetesResources(FLINK_DEPLOYMENT_API_VERSION, FLINK_DEPLOYMENT_KIND)
                .inNamespace(runtimeRef.getNamespace());
    }

    private List<Pod> pods(FlinkKubernetesRuntimeRef runtimeRef) {
        return client.pods()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(labelKey(runtimeRef.getPodLabelSelector()), labelValue(runtimeRef.getPodLabelSelector()))
                .list()
                .getItems();
    }

    private void deleteSecret(FlinkKubernetesRuntimeRef runtimeRef) {
        client.secrets()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getSecretName())
                .delete();
    }

    private StatusEnum mapOperatorState(String state, StatusEnum localState) {
        if (isBlank(state)) {
            return StatusEnum.RUNNING;
        }
        String normalized = state.trim().toUpperCase();
        return switch (normalized) {
            case "RUNNING", "CREATED", "INITIALIZING", "RECONCILING", "RESTARTING" -> StatusEnum.RUNNING;
            case "FINISHED" -> StatusEnum.RUN_SUCCESS;
            case "FAILED", "FAILING" -> StatusEnum.RUN_FAILURE;
            case "CANCELLING" -> StatusEnum.STOPPING;
            case "CANCELED", "SUSPENDED" -> localState == StatusEnum.STOPPING ? StatusEnum.STOP_SUCCESS
                    : StatusEnum.RUN_FAILURE;
            default -> StatusEnum.UNKNOWN;
        };
    }

    private Map<String, Object> objectMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> valueMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            valueMap.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String stringAt(GenericKubernetesResource resource, List<String> path) {
        Object value = resource.get(path.toArray());
        return value == null ? null : String.valueOf(value);
    }

    private static boolean hasCustomConfig(AgentProperties.Kubernetes kubernetes) {
        return !isBlank(kubernetes.getApiServer()) || !isBlank(kubernetes.getToken())
                || exists(kubernetes.getTokenFile()) || exists(kubernetes.getCaCertFile());
    }

    private String labelKey(String selector) {
        if (selector == null || !selector.contains("=")) {
            return FlinkK8sNameGenerator.TASK_LABEL;
        }
        return selector.substring(0, selector.indexOf('='));
    }

    private String labelValue(String selector) {
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
