package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flink Kubernetes Operator YAML template renderer.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Component
public class FlinkKubernetesTemplateRenderer {

    /**
     * Plugin type value.
     */
    private static final String PLUGIN_TYPE = "FLINK";

    /**
     * Run mode value.
     */
    private static final String RUN_MODE = "K8S_OPERATOR";

    /**
     * CPU resource key.
     */
    private static final String CPU_RESOURCE_KEY = "cpu";

    /**
     * Template renderer.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * Constructor.
     *
     * @param templateRenderer template renderer
     */
    public FlinkKubernetesTemplateRenderer(TemplateSpecRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    /**
     * Render Kubernetes YAML.
     *
     * @param param      execution parameter
     * @param jobContent job JSON content
     * @return rendered YAML
     */
    public String render(FlinkExecutionParam param, String jobContent) {
        FlinkKubernetesParam kubernetes = param.getKubernetes();
        Map<String, String> labels = labels(param);
        Map<String, String> annotations = annotations(kubernetes);
        String encodedJobContent = Base64.getEncoder().encodeToString(jobContent.getBytes(StandardCharsets.UTF_8));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("namespace", quote(kubernetes.getNamespace()));
        values.put("deploymentName", quote(kubernetes.getDeploymentName()));
        values.put("labels", mapYaml(labels, 4));
        values.put("deploymentLabels", mapYaml(labels, 4));
        values.put("podLabels", mapYaml(labels, 8));
        values.put("annotations", mapYaml(annotations, 4));
        values.put("podAnnotations", mapYaml(annotations, 8));
        values.put("image", quote(kubernetes.getImage()));
        values.put("imagePullPolicy", quote(kubernetes.getImagePullPolicy()));
        values.put("serviceAccountBlock", stringBlock("serviceAccount", kubernetes.getServiceAccountName(), 2));
        values.put("operatorFlinkVersion", quote(operatorFlinkVersion(param.getFlinkVersion())));
        values.put("flinkConfiguration", mapYaml(param.getFlinkConfig(), 4));
        values.put("nodeSelectorBlock", mapBlock("nodeSelector", kubernetes.getNodeSelector(), 6, 8));
        values.put("imagePullSecretsBlock", imagePullSecretsBlock(kubernetes.getImagePullSecrets(), 6, 8));
        values.put("envBlock", envBlock(kubernetes.getEnv(), 10, 12));
        values.put("envFromBlock", envFromBlock(kubernetes.getEnvFrom(), 10, 12));
        values.put("sharedMountPath", quote(kubernetes.getSharedMountPath()));
        values.put("sharedPvcName", quote(kubernetes.getSharedPvcName()));
        values.put("flinkAppDir", quote(kubernetes.getFlinkAppDir()));
        values.put("flinkAppJar", quote(param.getFlinkAppJar()));
        values.put("libDir", quote(param.getLibDir()));
        values.put("usrlibPath", quote(FlinkKubernetesTemplateConstants.USRLIB_PATH));
        values.put("jarUri", quote(kubernetes.getJarUri()));
        values.put("mainClass", quote(param.getMainClass()));
        values.put("args", listYaml(List.of("--job", encodedJobContent), 8));
        values.put("upgradeMode", quote(kubernetes.getUpgradeMode()));
        values.put("jobStateBlock", stringBlock("state", kubernetes.getJobState(), 4));
        values.put("jobParallelismBlock", integerBlock("parallelism", kubernetes.getJobParallelism(), 4));
        values.put("jobManagerReplicasBlock", integerBlock("replicas", kubernetes.getJobManagerReplicas(), 4));
        values.put("taskManagerReplicasBlock", integerBlock("replicas", kubernetes.getTaskManagerReplicas(), 4));
        values.put("jobManagerResource", quantityMapYaml(kubernetes.getJobManagerResource(), 6));
        values.put("taskManagerResource", quantityMapYaml(kubernetes.getTaskManagerResource(), 6));
        return templateRenderer.renderText(FlinkKubernetesTemplateConstants.TEMPLATE_PATH, values);
    }

    private Map<String, String> labels(FlinkExecutionParam param) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(FlinkK8sNameGenerator.PLUGIN_LABEL, PLUGIN_TYPE);
        labels.put(FlinkK8sNameGenerator.RUN_MODE_LABEL, RUN_MODE);
        labels.put(FlinkK8sNameGenerator.TASK_LABEL, FlinkK8sNameGenerator.labelValue(param.getTaskInstanceId()));
        labels.put(FlinkK8sNameGenerator.FLOW_LABEL, FlinkK8sNameGenerator.labelValue(param.getFlowInstanceId()));
        FlinkKubernetesParam kubernetes = param.getKubernetes();
        safeLabels(kubernetes.getLabels()).forEach(labels::putIfAbsent);
        return labels;
    }

    private Map<String, String> safeLabels(Map<String, String> labels) {
        Map<String, String> safe = new LinkedHashMap<>();
        labels.forEach((key, value) -> {
            if (!key.startsWith("datafusion.") && !key.startsWith("datafusion.io/")) {
                safe.put(key, FlinkK8sNameGenerator.labelValue(value));
            }
        });
        return safe;
    }

    private Map<String, String> annotations(FlinkKubernetesParam kubernetes) {
        Map<String, String> annotations = new LinkedHashMap<>(kubernetes.getAnnotations());
        annotations.put("datafusion.io/plugin-log-uri", pluginLogUri(kubernetes));
        annotations.put("datafusion.io/flink-web-ui-uri", kubernetes.getFlinkWebUiUri());
        return annotations;
    }

    private String envYaml(Map<String, String> env, int indent) {
        StringBuilder builder = new StringBuilder();
        env.forEach((key, value) -> builder.append(spaces(indent))
                .append("- name: ").append(quote(key)).append(System.lineSeparator())
                .append(spaces(indent + 2))
                .append("value: ").append(quote(value)).append(System.lineSeparator()));
        return builder.toString();
    }

    private String envBlock(Map<String, String> env, int keyIndent, int valueIndent) {
        if (env == null || env.isEmpty()) {
            return "";
        }
        return spaces(keyIndent) + "env:" + System.lineSeparator() + envYaml(env, valueIndent);
    }

    private String envFromBlock(JsonNode envFrom, int keyIndent, int valueIndent) {
        if (envFrom == null || !envFrom.isArray() || envFrom.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(spaces(keyIndent)).append("envFrom:")
                .append(System.lineSeparator());
        envFrom.forEach(item -> appendObjectRef(builder, item, "secretRef", valueIndent));
        envFrom.forEach(item -> appendObjectRef(builder, item, "configMapRef", valueIndent));
        return builder.toString();
    }

    private String imagePullSecretsBlock(JsonNode imagePullSecrets, int keyIndent, int valueIndent) {
        if (imagePullSecrets == null || !imagePullSecrets.isArray() || imagePullSecrets.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(spaces(keyIndent)).append("imagePullSecrets:")
                .append(System.lineSeparator());
        imagePullSecrets.forEach(item -> {
            String name = text(item, "name");
            if (!isBlank(name)) {
                builder.append(spaces(valueIndent)).append("- name: ").append(quote(name))
                        .append(System.lineSeparator());
            }
        });
        return builder.toString();
    }

    private void appendObjectRef(StringBuilder builder, JsonNode item, String refName, int indent) {
        JsonNode ref = item == null ? null : item.get(refName);
        String name = text(ref, "name");
        if (isBlank(name)) {
            return;
        }
        builder.append(spaces(indent)).append("- ").append(refName).append(':').append(System.lineSeparator())
                .append(spaces(indent + 4)).append("name: ").append(quote(name)).append(System.lineSeparator());
    }

    private String listYaml(List<String> values, int indent) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append(spaces(indent)).append("- ").append(quote(value)).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String quantityMapYaml(JsonNode node, int indent) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        node.properties().forEach(entry -> builder.append(spaces(indent))
                .append(quote(entry.getKey())).append(": ").append(resourceValue(entry.getKey(), entry.getValue()))
                .append(System.lineSeparator()));
        return builder.toString();
    }

    private String resourceValue(String key, JsonNode value) {
        if (CPU_RESOURCE_KEY.equals(key)) {
            return numericResourceValue(value);
        }
        return quote(value == null ? "" : value.asText());
    }

    private String numericResourceValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "0";
        }
        if (value.isNumber()) {
            return value.asText();
        }
        String text = value.asText();
        try {
            return new BigDecimal(text).toPlainString();
        } catch (NumberFormatException ignored) {
            return quote(text);
        }
    }

    private String mapBlock(String name, Map<String, String> map, int keyIndent, int valueIndent) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return spaces(keyIndent) + name + ':' + System.lineSeparator() + mapYaml(map, valueIndent);
    }

    private String mapYaml(Map<String, String> map, int indent) {
        if (map == null || map.isEmpty()) {
            return spaces(indent) + "{}" + System.lineSeparator();
        }
        StringBuilder builder = new StringBuilder();
        map.forEach((key, value) -> builder.append(spaces(indent))
                .append(quote(key)).append(": ").append(quote(value)).append(System.lineSeparator()));
        return builder.toString();
    }

    private String stringBlock(String name, String value, int indent) {
        if (isBlank(value)) {
            return "";
        }
        return spaces(indent) + name + ": " + quote(value) + System.lineSeparator();
    }

    private String integerBlock(String name, Integer value, int indent) {
        if (value == null) {
            return "";
        }
        return spaces(indent) + name + ": " + value + System.lineSeparator();
    }

    private String operatorFlinkVersion(String flinkVersion) {
        if ("2.2.0".equals(flinkVersion)) {
            return "v2_2";
        }
        throw new IllegalArgumentException("暂不支持的Flink版本: " + flinkVersion);
    }

    private String pluginLogUri(FlinkKubernetesParam kubernetes) {
        if (!isBlank(kubernetes.getLogStorageUri())) {
            return kubernetes.getLogStorageUri();
        }
        return "k8s-operator://" + kubernetes.getNamespace() + "/flinkdeployments/"
                + kubernetes.getDeploymentName();
    }

    private String quote(String value) {
        String text = value == null ? "" : value;
        String escaped = text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return '"' + escaped + '"';
    }

    private String spaces(int count) {
        return " ".repeat(Math.max(count, 0));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
