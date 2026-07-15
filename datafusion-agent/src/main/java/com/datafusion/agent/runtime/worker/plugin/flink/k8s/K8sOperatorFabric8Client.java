package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
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
 * Fabric8 Flink Kubernetes Operator client.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Slf4j
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
     * FlinkDeployment spec property.
     */
    private static final String SPEC_PROPERTY = "spec";

    /**
     * FlinkDeployment job property.
     */
    private static final String JOB_PROPERTY = "job";

    /**
     * FlinkDeployment job state property.
     */
    private static final String STATE_PROPERTY = "state";

    /**
     * Flink Operator suspended state.
     */
    private static final String OPERATOR_SUSPENDED_STATE = "SUSPENDED";

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
        String jobContent = writeJobJson(param);
        String manifest = templateRenderer.render(param, jobContent);
        writeYml(param, manifest);
        validateSharedPluginFiles(param);
        FlinkKubernetesParam kubernetes = param.getKubernetes();
        log.info("提交K8S_OPERATOR FlinkDeployment, namespace={}, deploymentName={}, image={}, jarURI={}, "
                + "manifestPath={}",
                kubernetes.getNamespace(), kubernetes.getDeploymentName(), kubernetes.getImage(),
                kubernetes.getJarUri(),
                param.getWorkDir().resolve(FlinkKubernetesTemplateConstants.MANIFEST_FILE_NAME));
        client.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).createOrReplace();
        log.info("K8S_OPERATOR FlinkDeployment提交完成, namespace={}, deploymentName={}, flinkWebUiUri={}",
                kubernetes.getNamespace(), kubernetes.getDeploymentName(), kubernetes.getFlinkWebUiUri());
        return FlinkKubernetesRuntimeRef.builder()
                .namespace(kubernetes.getNamespace())
                .deploymentName(kubernetes.getDeploymentName())
                .podLabelSelector(kubernetes.getPodLabelSelector())
                .logStorageUri(kubernetes.getLogStorageUri())
                .flinkWebUiUri(kubernetes.getFlinkWebUiUri())
                .collectLogsOnFinish(kubernetes.isCollectLogsOnFinish())
                .deleteDeploymentOnFinish(kubernetes.isDeleteDeploymentOnFinish())
                .build();
    }

    @Override
    public void stop(FlinkKubernetesRuntimeRef runtimeRef) {
        GenericKubernetesResource deployment = deployment(runtimeRef);
        if (deployment == null) {
            log.info("K8S_OPERATOR FlinkDeployment不存在, 跳过停止, namespace={}, deploymentName={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
            return;
        }
        Map<String, Object> spec = objectMap(deployment.getAdditionalProperties(), SPEC_PROPERTY);
        Map<String, Object> job = objectMap(spec, JOB_PROPERTY);
        job.put(STATE_PROPERTY, OPERATOR_SUSPENDED_STATE);
        spec.put(JOB_PROPERTY, job);
        deployment.setAdditionalProperty(SPEC_PROPERTY, spec);
        deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).replace(deployment);
        log.info("K8S_OPERATOR FlinkDeployment已更新为SUSPENDED, namespace={}, deploymentName={}",
                runtimeRef.getNamespace(), runtimeRef.getDeploymentName());
    }

    @Override
    public void kill(FlinkKubernetesRuntimeRef runtimeRef) {
        cleanupRuntimeResources(runtimeRef, true);
    }

    @Override
    public FlinkOperatorStatus queryStatus(FlinkKubernetesRuntimeRef runtimeRef) {
        GenericKubernetesResource deployment = deployment(runtimeRef);
        List<Pod> podList = pods(runtimeRef);
        List<Service> serviceList = services(runtimeRef);
        String state = deployment == null ? null : stringAt(deployment, JOB_STATUS_PATH);
        FlinkOperatorStatus.State operatorState = FlinkOperatorStatus.State.from(state);
        if (operatorState == FlinkOperatorStatus.State.UNKNOWN) {
            log.warn("K8S_OPERATOR FlinkDeployment返回未知状态, namespace={}, deploymentName={}, operatorState={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), state);
        }
        log.debug("查询K8S_OPERATOR FlinkDeployment状态, namespace={}, deploymentName={}, operatorState={}, "
                        + "deploymentExists={}, podExists={}, serviceExists={}",
                runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), state, deployment != null,
                !podList.isEmpty(), !serviceList.isEmpty());
        return FlinkOperatorStatus.builder()
                .state(operatorState)
                .deploymentExists(deployment != null)
                .podExists(!podList.isEmpty())
                .serviceExists(!serviceList.isEmpty())
                .build();
    }

    @Override
    public String collectLogs(FlinkKubernetesRuntimeRef runtimeRef) {
        StringBuilder builder = new StringBuilder();
        List<Pod> podList = pods(runtimeRef);
        log.info("采集K8S_OPERATOR Flink Pod日志, namespace={}, deploymentName={}, podCount={}",
                runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), podList.size());
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
    public boolean cleanup(FlinkKubernetesRuntimeRef runtimeRef) {
        try {
            if (runtimeRef.isDeleteDeploymentOnFinish()) {
                cleanupRuntimeResources(runtimeRef, false);
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("清理K8S_OPERATOR Flink运行资源失败, namespace={}, deploymentName={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), e);
            return false;
        }
    }

    /**
     * Build Kubernetes client config.
     *
     * @param kubernetes Kubernetes config
     * @return Fabric8 config, or null when using default config
     */
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

    /**
     * Validate shared Flink plugin files before submitting FlinkDeployment.
     *
     * @param param execution parameter
     */
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
        if (Files.exists(libDir) && !Files.isDirectory(libDir)) {
            throw new IllegalArgumentException("共享盘Flink app依赖路径不是目录: " + libDir);
        }
        log.info("K8S_OPERATOR Flink共享盘文件校验通过, appDir={}, mainJar={}, libDirExists={}",
                appDir, mainJar, Files.exists(libDir));
    }

    /**
     * Write task job JSON snapshot to work directory.
     *
     * @param param execution parameter
     * @return job JSON content
     */
    private String writeJobJson(FlinkExecutionParam param) {
        try {
            Files.createDirectories(param.getWorkDir());
            String content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(param.getEffectiveTaskData());
            Files.writeString(param.getWorkDir()
                    .resolve(FlinkKubernetesTemplateConstants.JOB_JSON_FILE_NAME), content, StandardCharsets.UTF_8);
            log.info("写入K8S_OPERATOR Flink job JSON快照, taskInstanceId={}, path={}, bytes={}",
                    param.getTaskInstanceId(), param.getWorkDir()
                            .resolve(FlinkKubernetesTemplateConstants.JOB_JSON_FILE_NAME),
                    content.getBytes(StandardCharsets.UTF_8).length);
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("写入Flink job.json失败: " + e.getMessage(), e);
        }
    }

    /**
     * Write rendered FlinkDeployment YAML to work directory.
     *
     * @param param    execution parameter
     * @param manifest rendered YAML content
     */
    private void writeYml(FlinkExecutionParam param, String manifest) {
        try {
            Files.createDirectories(param.getWorkDir());
            Files.writeString(param.getWorkDir().resolve(FlinkKubernetesTemplateConstants.MANIFEST_FILE_NAME),
                    manifest, StandardCharsets.UTF_8);
            log.info("写入K8S_OPERATOR Flink deployment YAML, taskInstanceId={}, path={}, bytes={}",
                    param.getTaskInstanceId(),
                    param.getWorkDir().resolve(FlinkKubernetesTemplateConstants.MANIFEST_FILE_NAME),
                    manifest.getBytes(StandardCharsets.UTF_8).length);
        } catch (Exception e) {
            throw new IllegalStateException("写入Flink K8S_OPERATOR deployment yaml失败: " + e.getMessage(), e);
        }
    }

    /**
     * Get FlinkDeployment resource.
     *
     * @param runtimeRef runtime reference
     * @return FlinkDeployment resource, or null if absent
     */
    private GenericKubernetesResource deployment(FlinkKubernetesRuntimeRef runtimeRef) {
        return deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).get();
    }

    /**
     * Build FlinkDeployment resource client.
     *
     * @param runtimeRef runtime reference
     * @return namespaced FlinkDeployment client
     */
    private io.fabric8.kubernetes.client.dsl.NonNamespaceOperation<GenericKubernetesResource,
            io.fabric8.kubernetes.api.model.GenericKubernetesResourceList,
            io.fabric8.kubernetes.client.dsl.Resource<GenericKubernetesResource>> deployments(
            FlinkKubernetesRuntimeRef runtimeRef) {
        return client.genericKubernetesResources(FLINK_DEPLOYMENT_API_VERSION, FLINK_DEPLOYMENT_KIND)
                .inNamespace(runtimeRef.getNamespace());
    }

    /**
     * List runtime Pods by DataFusion task label.
     *
     * @param runtimeRef runtime reference
     * @return runtime Pods
     */
    private List<Pod> pods(FlinkKubernetesRuntimeRef runtimeRef) {
        return client.pods()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(labelKey(runtimeRef.getPodLabelSelector()), labelValue(runtimeRef.getPodLabelSelector()))
                .list()
                .getItems();
    }

    /**
     * List runtime Services by DataFusion task label.
     *
     * @param runtimeRef runtime reference
     * @return runtime Services
     */
    private List<Service> services(FlinkKubernetesRuntimeRef runtimeRef) {
        return client.services()
                .inNamespace(runtimeRef.getNamespace())
                .withLabel(labelKey(runtimeRef.getPodLabelSelector()), labelValue(runtimeRef.getPodLabelSelector()))
                .list()
                .getItems();
    }

    /**
     * Cleanup K8S runtime resources idempotently.
     *
     * @param runtimeRef runtime reference
     * @param force      whether to delete resources immediately
     */
    private void cleanupRuntimeResources(FlinkKubernetesRuntimeRef runtimeRef, boolean force) {
        GenericKubernetesResource deployment = deployment(runtimeRef);
        List<Pod> podList = pods(runtimeRef);
        List<Service> serviceList = services(runtimeRef);
        if (isAllResourceAbsent(deployment, podList, serviceList)) {
            log.info("K8S_OPERATOR Flink运行资源已不存在, namespace={}, deploymentName={}, force={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), force);
            return;
        }
        if (deployment != null) {
            log.info("清理K8S_OPERATOR FlinkDeployment, namespace={}, deploymentName={}, force={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), force);
            if (force) {
                deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).withGracePeriod(0L).delete();
            } else {
                deployments(runtimeRef).withName(runtimeRef.getDeploymentName()).delete();
            }
        }
        if (!podList.isEmpty()) {
            log.info("清理K8S_OPERATOR Flink Pod, namespace={}, deploymentName={}, podCount={}, force={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), podList.size(), force);
            // 按名称删除，避免标签批量删除触发 Kubernetes deletecollection 权限。
            for (Pod pod : podList) {
                if (force) {
                    client.pods()
                            .inNamespace(runtimeRef.getNamespace())
                            .withName(pod.getMetadata().getName())
                            .withGracePeriod(0L)
                            .delete();
                } else {
                    client.pods()
                            .inNamespace(runtimeRef.getNamespace())
                            .withName(pod.getMetadata().getName())
                            .delete();
                }
            }
        }
        if (!serviceList.isEmpty()) {
            log.info("清理K8S_OPERATOR Flink Service, namespace={}, deploymentName={}, serviceCount={}, force={}",
                    runtimeRef.getNamespace(), runtimeRef.getDeploymentName(), serviceList.size(), force);
            for (Service service : serviceList) {
                client.services()
                        .inNamespace(runtimeRef.getNamespace())
                        .withName(service.getMetadata().getName())
                        .delete();
            }
        }
    }

    private boolean isAllResourceAbsent(GenericKubernetesResource deployment, List<Pod> podList,
            List<Service> serviceList) {
        return deployment == null && podList.isEmpty() && serviceList.isEmpty();
    }

    /**
     * Read nested object map.
     *
     * @param map source map
     * @param key object key
     * @return copied object map, or empty map if absent
     */
    private Map<String, Object> objectMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> valueMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            valueMap.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue));
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * Read string value by Kubernetes resource path.
     *
     * @param resource Kubernetes resource
     * @param path     nested property path
     * @return string value, or null if absent
     */
    private String stringAt(GenericKubernetesResource resource, List<String> path) {
        Object value = resource.get(path.toArray());
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Check whether custom Kubernetes client config is provided.
     *
     * @param kubernetes Kubernetes config
     * @return true if custom config exists
     */
    private static boolean hasCustomConfig(AgentProperties.Kubernetes kubernetes) {
        return !isBlank(kubernetes.getApiServer()) || !isBlank(kubernetes.getToken())
                || exists(kubernetes.getTokenFile()) || exists(kubernetes.getCaCertFile());
    }

    /**
     * Resolve label selector key.
     *
     * @param selector selector text
     * @return label key
     */
    private String labelKey(String selector) {
        if (selector == null || !selector.contains(SystemConstant.EQ)) {
            return FlinkK8sNameGenerator.TASK_LABEL;
        }
        return selector.substring(0, selector.indexOf(SystemConstant.EQ));
    }

    /**
     * Resolve label selector value.
     *
     * @param selector selector text
     * @return label value
     */
    private String labelValue(String selector) {
        if (selector == null || !selector.contains(SystemConstant.EQ)) {
            return SystemConstant.BLANK;
        }
        return selector.substring(selector.indexOf(SystemConstant.EQ) + SystemConstant.EQ.length());
    }

    /**
     * Check whether text is blank.
     *
     * @param value text value
     * @return true if text is blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Check whether filesystem path exists.
     *
     * @param path filesystem path
     * @return true if path exists
     */
    private static boolean exists(String path) {
        return !isBlank(path) && Files.exists(Path.of(path));
    }
}
