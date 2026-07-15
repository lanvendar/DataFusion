package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.common.constant.SystemConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric8 Spark Operator 客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Slf4j
@Component("sparkK8sOperatorFabric8Client")
public class K8sOperatorFabric8Client implements K8sOperatorClient {

    /**
     * SparkApplication apiVersion.
     */
    private static final String SPARK_APPLICATION_API_VERSION = "sparkoperator.k8s.io/v1beta2";

    /**
     * SparkApplication kind.
     */
    private static final String SPARK_APPLICATION_KIND = "SparkApplication";

    /**
     * SparkApplication 状态路径.
     */
    private static final List<String> APPLICATION_STATE_PATH = List.of("status", "applicationState", "state");

    /**
     * spec 字段.
     */
    private static final String SPEC_PROPERTY = "spec";

    /**
     * suspend 字段.
     */
    private static final String SUSPEND_PROPERTY = "suspend";

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Kubernetes client.
     */
    private final KubernetesClient client;

    /**
     * 模板渲染器.
     */
    private final SparkKubernetesTemplateRenderer templateRenderer;

    /**
     * 构造函数.
     *
     * @param properties       Agent 配置
     * @param templateRenderer 模板渲染器
     */
    public K8sOperatorFabric8Client(AgentProperties properties, SparkKubernetesTemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        Config config = config(properties.getKubernetes());
        this.client = config == null ? new KubernetesClientBuilder().build()
                : new KubernetesClientBuilder().withConfig(config).build();
    }

    @Override
    public SparkKubernetesRuntimeRef submit(SparkExecutionParam param) {
        String jobContent = writeJobJson(param);
        validateSharedPluginFiles(param);
        String manifest = templateRenderer.render(param, jobContent);
        writeManifest(param, manifest);
        SparkKubernetesParam kubernetes = param.getKubernetes();
        log.info("提交SPARK_K8S_OPERATOR SparkApplication, namespace={}, applicationName={}, configMapName={}",
                kubernetes.getNamespace(), kubernetes.getApplicationName(), kubernetes.getConfigMapName());
        client.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).createOrReplace();
        return runtimeRef(kubernetes);
    }

    @Override
    public void stop(SparkKubernetesRuntimeRef runtimeRef) {
        GenericKubernetesResource application = application(runtimeRef);
        if (application == null) {
            log.info("SPARK_K8S_OPERATOR SparkApplication不存在, 跳过停止, namespace={}, applicationName={}",
                    runtimeRef.getNamespace(), runtimeRef.getApplicationName());
            return;
        }
        Map<String, Object> spec = objectMap(application.getAdditionalProperties(), SPEC_PROPERTY);
        spec.put(SUSPEND_PROPERTY, Boolean.TRUE);
        application.setAdditionalProperty(SPEC_PROPERTY, spec);
        applications(runtimeRef).withName(runtimeRef.getApplicationName()).replace(application);
        log.info("SPARK_K8S_OPERATOR SparkApplication已更新为suspend, namespace={}, applicationName={}",
                runtimeRef.getNamespace(), runtimeRef.getApplicationName());
    }

    @Override
    public void kill(SparkKubernetesRuntimeRef runtimeRef) {
        cleanupRuntimeResources(runtimeRef, true, true);
    }

    @Override
    public SparkOperatorStatus queryStatus(SparkKubernetesRuntimeRef runtimeRef) {
        GenericKubernetesResource application = application(runtimeRef);
        List<Pod> podList = pods(runtimeRef);
        List<Service> serviceList = services(runtimeRef);
        String state = application == null ? null : stringAt(application, APPLICATION_STATE_PATH);
        SparkOperatorStatus.State applicationState = SparkOperatorStatus.State.from(state);
        if (applicationState == SparkOperatorStatus.State.UNKNOWN) {
            log.warn("SPARK_K8S_OPERATOR 返回未知状态, namespace={}, applicationName={}, state={}",
                    runtimeRef.getNamespace(), runtimeRef.getApplicationName(), state);
        }
        return SparkOperatorStatus.builder()
                .state(applicationState)
                .applicationExists(application != null)
                .podExists(!podList.isEmpty())
                .podRunning(podList.stream().anyMatch(this::podRunning))
                .serviceExists(!serviceList.isEmpty())
                .build();
    }

    @Override
    public String collectLogs(SparkKubernetesRuntimeRef runtimeRef) {
        StringBuilder builder = new StringBuilder();
        List<Pod> podList = pods(runtimeRef);
        log.info("采集SPARK_K8S_OPERATOR Pod日志, namespace={}, applicationName={}, podCount={}",
                runtimeRef.getNamespace(), runtimeRef.getApplicationName(), podList.size());
        for (Pod pod : podList) {
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
    public boolean cleanup(SparkKubernetesRuntimeRef runtimeRef) {
        try {
            cleanupRuntimeResources(runtimeRef, false, true);
            deleteConfigMap(runtimeRef);
            return true;
        } catch (RuntimeException e) {
            log.warn("清理SPARK_K8S_OPERATOR资源失败, namespace={}, applicationName={}, error={}",
                    runtimeRef.getNamespace(), runtimeRef.getApplicationName(), e.getMessage());
            return false;
        }
    }

    private SparkKubernetesRuntimeRef runtimeRef(SparkKubernetesParam kubernetes) {
        return SparkKubernetesRuntimeRef.builder()
                .namespace(kubernetes.getNamespace())
                .applicationName(kubernetes.getApplicationName())
                .configMapName(kubernetes.getConfigMapName())
                .podLabelSelector(kubernetes.getPodLabelSelector())
                .logStorageUri(kubernetes.getLogStorageUri())
                .sparkWebUiUri(kubernetes.getSparkWebUiUri())
                .collectLogsOnFinish(kubernetes.isCollectLogsOnFinish())
                .build();
    }

    private void validateSharedPluginFiles(SparkExecutionParam param) {
        SparkKubernetesParam kubernetes = param.getKubernetes();
        Path appDir = Path.of(kubernetes.getPluginAppDir());
        if (!Files.isDirectory(appDir)) {
            throw new IllegalArgumentException("共享盘Spark插件目录不存在: " + appDir);
        }
        Path pluginJar = appDir.resolve(kubernetes.getPluginJarName());
        if (!Files.isRegularFile(pluginJar)) {
            throw new IllegalArgumentException("共享盘Spark插件jar不存在: " + pluginJar);
        }
    }

    private String writeJobJson(SparkExecutionParam param) {
        try {
            Files.createDirectories(param.getWorkDir());
            String content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(param.getEffectiveTaskData());
            Files.writeString(param.getWorkDir()
                    .resolve(SparkKubernetesTemplateConstants.JOB_JSON_FILE_NAME), content, StandardCharsets.UTF_8);
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("写入Spark job JSON失败: " + e.getMessage(), e);
        }
    }

    private void writeManifest(SparkExecutionParam param, String manifest) {
        try {
            Files.createDirectories(param.getWorkDir());
            Files.writeString(param.getWorkDir().resolve(SparkKubernetesTemplateConstants.MANIFEST_FILE_NAME),
                    manifest, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("写入Spark K8S manifest失败: " + e.getMessage(), e);
        }
    }

    private GenericKubernetesResource application(SparkKubernetesRuntimeRef runtimeRef) {
        return applications(runtimeRef).withName(runtimeRef.getApplicationName()).get();
    }

    private io.fabric8.kubernetes.client.dsl.NonNamespaceOperation<GenericKubernetesResource,
            io.fabric8.kubernetes.api.model.GenericKubernetesResourceList,
            io.fabric8.kubernetes.client.dsl.Resource<GenericKubernetesResource>> applications(
            SparkKubernetesRuntimeRef runtimeRef) {
        return client.genericKubernetesResources(SPARK_APPLICATION_API_VERSION, SPARK_APPLICATION_KIND)
                .inNamespace(runtimeRef.getNamespace());
    }

    private List<Pod> pods(SparkKubernetesRuntimeRef runtimeRef) {
        return client.pods()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(labelKey(runtimeRef.getPodLabelSelector()), labelValue(runtimeRef.getPodLabelSelector()))
                .list()
                .getItems();
    }

    private List<Service> services(SparkKubernetesRuntimeRef runtimeRef) {
        return client.services()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(labelKey(runtimeRef.getPodLabelSelector()), labelValue(runtimeRef.getPodLabelSelector()))
                .list()
                .getItems();
    }

    private void cleanupRuntimeResources(SparkKubernetesRuntimeRef runtimeRef, boolean force, boolean deleteApplication) {
        if (deleteApplication && application(runtimeRef) != null) {
            if (force) {
                applications(runtimeRef).withName(runtimeRef.getApplicationName()).withGracePeriod(0L).delete();
            } else {
                applications(runtimeRef).withName(runtimeRef.getApplicationName()).delete();
            }
        }
        if (force) {
            // 按名称删除，避免标签批量删除触发 Kubernetes deletecollection 权限。
            for (Pod pod : pods(runtimeRef)) {
                client.pods()
                        .inNamespace(runtimeRef.getNamespace())
                        .withName(pod.getMetadata().getName())
                        .withGracePeriod(0L)
                        .delete();
            }
        }
        if (force) {
            deleteConfigMap(runtimeRef);
        }
    }

    private void deleteConfigMap(SparkKubernetesRuntimeRef runtimeRef) {
        client.configMaps()
                .inNamespace(runtimeRef.getNamespace())
                .withName(runtimeRef.getConfigMapName())
                .delete();
    }

    private boolean podRunning(Pod pod) {
        if (pod.getStatus() == null) {
            return false;
        }
        String phase = pod.getStatus().getPhase();
        return "Running".equals(phase) || "Pending".equals(phase);
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

    private String labelKey(String selector) {
        if (selector == null || !selector.contains(SystemConstant.EQ)) {
            return SparkK8sNameGenerator.TASK_LABEL;
        }
        return selector.substring(0, selector.indexOf(SystemConstant.EQ));
    }

    private String labelValue(String selector) {
        if (selector == null || !selector.contains(SystemConstant.EQ)) {
            return SystemConstant.BLANK;
        }
        return selector.substring(selector.indexOf(SystemConstant.EQ) + SystemConstant.EQ.length());
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

    private static boolean hasCustomConfig(AgentProperties.Kubernetes kubernetes) {
        return !isBlank(kubernetes.getApiServer()) || !isBlank(kubernetes.getToken())
                || exists(kubernetes.getTokenFile()) || exists(kubernetes.getCaCertFile());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean exists(String path) {
        return !isBlank(path) && Files.exists(Path.of(path));
    }
}
