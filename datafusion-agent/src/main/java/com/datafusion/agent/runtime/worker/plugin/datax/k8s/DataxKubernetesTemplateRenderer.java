package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DataX Kubernetes YAML template renderer.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class DataxKubernetesTemplateRenderer {

    /**
     * Plugin type value.
     */
    private static final String PLUGIN_TYPE = "DATAX";

    /**
     * Template renderer.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * Constructor.
     *
     * @param templateRenderer template renderer
     */
    public DataxKubernetesTemplateRenderer(TemplateSpecRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    /**
     * Render Kubernetes YAML.
     *
     * @param param      execution param
     * @param jobContent DataX job JSON content
     * @return Kubernetes YAML
     */
    public String render(DataxExecutionParam param, String jobContent) {
        DataxKubernetesParam kubernetes = param.getKubernetes();
        Map<String, String> labels = labels(param);
        Map<String, String> annotations = annotations(kubernetes);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("namespace", quote(kubernetes.getNamespace()));
        values.put("secretName", quote(kubernetes.getSecretName()));
        values.put("jobName", quote(kubernetes.getJobName()));
        values.put("jobSecretKey", DataxKubernetesTemplateConstants.JOB_SECRET_KEY);
        values.put("jobJsonBase64", quote(Base64.getEncoder()
                .encodeToString(jobContent.getBytes(StandardCharsets.UTF_8))));
        values.put("jobJsonMountDir", quote(DataxKubernetesTemplateConstants.JOB_JSON_MOUNT_DIR));
        values.put("labels", mapYaml(labels, 4));
        values.put("podLabels", mapYaml(labels, 8));
        values.put("annotations", mapYaml(annotations, 4));
        values.put("podAnnotations", mapYaml(annotations, 8));
        values.put("backoffLimit", String.valueOf(kubernetes.getBackoffLimit()));
        values.put("ttlSecondsAfterFinishedBlock", integerBlock("ttlSecondsAfterFinished",
                kubernetes.getTtlSecondsAfterFinished(), 2));
        values.put("activeDeadlineSecondsBlock", longBlock("activeDeadlineSeconds",
                kubernetes.getActiveDeadlineSeconds(), 2));
        values.put("serviceAccountNameBlock", stringBlock("serviceAccountName",
                kubernetes.getServiceAccountName(), 6));
        values.put("nodeSelectorBlock", mapBlock("nodeSelector", kubernetes.getNodeSelector(), 6, 8));
        values.put("image", quote(kubernetes.getImage()));
        values.put("imagePullPolicy", quote(kubernetes.getImagePullPolicy()));
        values.put("env", envYaml(param, 12));
        values.put("resourcesBlock", resourcesBlock(kubernetes.getResources(), 10));
        return templateRenderer.renderText(DataxKubernetesTemplateConstants.TEMPLATE_PATH, values);
    }

    private Map<String, String> labels(DataxExecutionParam param) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(DataxK8sNameGenerator.PLUGIN_LABEL, PLUGIN_TYPE);
        labels.put(DataxK8sNameGenerator.RUN_MODE_LABEL, DataxRunMode.K8S.name());
        labels.put(DataxK8sNameGenerator.TASK_LABEL,
                labelValueFromSelector(param.getKubernetes().getPodLabelSelector()));
        labels.put(DataxK8sNameGenerator.FLOW_LABEL, DataxK8sNameGenerator.labelValue(param.getFlowInstanceId()));
        safeLabels(param.getKubernetes().getLabels()).forEach(labels::putIfAbsent);
        return labels;
    }

    private Map<String, String> safeLabels(Map<String, String> labels) {
        Map<String, String> safe = new LinkedHashMap<>();
        labels.forEach((key, value) -> {
            if (!key.startsWith("datafusion.") && !key.startsWith("datafusion.io/")) {
                safe.put(key, DataxK8sNameGenerator.labelValue(value));
            }
        });
        return safe;
    }

    private Map<String, String> annotations(DataxKubernetesParam kubernetes) {
        Map<String, String> annotations = new LinkedHashMap<>(kubernetes.getAnnotations());
        annotations.put("datafusion.io/worker-id", "");
        annotations.put("datafusion.io/plugin-log-uri", pluginLogUri(kubernetes));
        return annotations;
    }

    private String envYaml(DataxExecutionParam param, int indent) {
        DataxKubernetesParam kubernetes = param.getKubernetes();
        Map<String, String> env = new LinkedHashMap<>(kubernetes.getEnv());
        env.put("DATAX_HOME", kubernetes.getDataxHome());
        env.put("DATAX_JOB_FILE", kubernetes.getJobJsonMountPath());
        env.put("DATAX_LOG_FILE", DataxKubernetesTemplateConstants.DATAX_LOG_FILE);
        env.put("DATAX_LOG_LEVEL", param.getLogLevel());
        env.put("DATAX_LOG_MAX_SIZE", param.getLogMaxSize());
        env.put("DATAX_LOG_MAX_INDEX", String.valueOf(param.getLogMaxIndex()));
        env.put("DATAX_JOB_ID", param.getJobId());
        env.put("JAVA_OPTS", param.getJvmOptions() == null ? "" : String.join(" ", param.getJvmOptions()));
        StringBuilder builder = new StringBuilder();
        env.forEach((key, value) -> builder.append(spaces(indent))
                .append("- name: ").append(quote(key)).append(System.lineSeparator())
                .append(spaces(indent + 2))
                .append("value: ").append(quote(value)).append(System.lineSeparator()));
        return builder.toString();
    }

    private String resourcesBlock(JsonNode node, int indent) {
        if (node == null || !node.isObject()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(spaces(indent)).append("resources:").append(System.lineSeparator());
        appendQuantityMap(builder, "requests", node.get("requests"), indent + 2);
        appendQuantityMap(builder, "limits", node.get("limits"), indent + 2);
        return builder.toString();
    }

    private void appendQuantityMap(StringBuilder builder, String name, JsonNode node, int indent) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return;
        }
        builder.append(spaces(indent)).append(name).append(':').append(System.lineSeparator());
        node.properties().forEach(entry -> builder.append(spaces(indent + 2))
                .append(quote(entry.getKey())).append(": ")
                .append(quote(entry.getValue().asText())).append(System.lineSeparator()));
    }

    private String mapBlock(String name, Map<String, String> map, int keyIndent, int valueIndent) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return spaces(keyIndent) + name + ':' + System.lineSeparator() + mapYaml(map, valueIndent);
    }

    private String mapYaml(Map<String, String> map, int indent) {
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

    private String longBlock(String name, Long value, int indent) {
        if (value == null) {
            return "";
        }
        return spaces(indent) + name + ": " + value + System.lineSeparator();
    }

    private String pluginLogUri(DataxKubernetesParam kubernetes) {
        if (!isBlank(kubernetes.getLogStorageUri())) {
            return kubernetes.getLogStorageUri();
        }
        return "k8s://" + kubernetes.getNamespace() + "/jobs/" + kubernetes.getJobName();
    }

    private String labelValueFromSelector(String selector) {
        if (selector == null || !selector.contains("=")) {
            return "";
        }
        return selector.substring(selector.indexOf('=') + 1);
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
}
