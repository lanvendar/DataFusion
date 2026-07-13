package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spark Kubernetes 模板渲染器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Component
public class SparkKubernetesTemplateRenderer {

    /**
     * 插件类型.
     */
    private static final String PLUGIN_TYPE = "SPARK";

    /**
     * 运行模式.
     */
    private static final String RUN_MODE = "K8S_OPERATOR";

    /**
     * 模板渲染器.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * 构造函数.
     *
     * @param templateRenderer 模板渲染器
     */
    public SparkKubernetesTemplateRenderer(TemplateSpecRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    /**
     * 渲染 Kubernetes manifest.
     *
     * @param param      执行参数
     * @param jobContent job JSON 内容
     * @return manifest
     */
    public String render(SparkExecutionParam param, String jobContent) {
        SparkKubernetesParam kubernetes = param.getKubernetes();
        Map<String, String> labels = labels(param);
        Map<String, String> annotations = annotations(kubernetes);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("namespace", quote(kubernetes.getNamespace()));
        values.put("configMapName", quote(kubernetes.getConfigMapName()));
        values.put("jobConfigData", literalBlock(jobContent, 4));
        values.put("applicationName", quote(kubernetes.getApplicationName()));
        values.put("labels", mapYaml(labels, 4));
        values.put("applicationLabels", mapYaml(labels, 4));
        values.put("driverLabels", mapYaml(labels, 8));
        values.put("executorLabels", mapYaml(labels, 8));
        values.put("annotations", mapYaml(annotations, 4));
        values.put("image", quote(kubernetes.getImage()));
        values.put("imagePullPolicy", quote(kubernetes.getImagePullPolicy()));
        values.put("serviceAccountBlock", stringBlock("serviceAccount", kubernetes.getServiceAccountName(), 4));
        values.put("sparkVersion", quote(param.getSparkVersion()));
        values.put("mainClass", quote(param.getMainClass()));
        values.put("mainApplicationFile", quote(param.getMainApplicationFile()));
        values.put("arguments", listYaml(param.getArguments(), 4));
        values.put("sparkConf", mapYaml(param.getSparkConf(), 4));
        values.put("hadoopConf", mapYaml(param.getHadoopConf(), 4));
        values.put("nodeSelectorBlock", mapBlock("nodeSelector", kubernetes.getNodeSelector(), 4, 6));
        values.put("sharedPvcName", quote(kubernetes.getSharedPvcName()));
        values.put("sharedMountPath", quote(kubernetes.getSharedMountPath()));
        values.put("pluginAppDir", quote(kubernetes.getPluginAppDir()));
        values.put("jarMountPath", quote(kubernetes.getJarMountPath()));
        values.put("jobConfigMountPath", quote(kubernetes.getJobConfigMountPath()));
        values.put("jobFileName", quote(SparkKubernetesTemplateConstants.JOB_JSON_FILE_NAME));
        values.put("driverCores", integerText(kubernetes.getDriver(), "cores", "1"));
        values.put("driverMemory", quote(text(kubernetes.getDriver(), "memory", "1g")));
        values.put("executorInstances", integerText(kubernetes.getExecutor(), "instances", "1"));
        values.put("executorCores", integerText(kubernetes.getExecutor(), "cores", "1"));
        values.put("executorMemory", quote(text(kubernetes.getExecutor(), "memory", "1g")));
        return templateRenderer.renderText(SparkKubernetesTemplateConstants.TEMPLATE_PATH, values);
    }

    private Map<String, String> labels(SparkExecutionParam param) {
        SparkKubernetesParam kubernetes = param.getKubernetes();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(SparkK8sNameGenerator.PLUGIN_LABEL, PLUGIN_TYPE);
        labels.put(SparkK8sNameGenerator.RUN_MODE_LABEL, RUN_MODE);
        labels.put(SparkK8sNameGenerator.TASK_LABEL, SparkK8sNameGenerator.labelValue(param.getTaskInstanceId()));
        labels.put(SparkK8sNameGenerator.FLOW_LABEL, SparkK8sNameGenerator.labelValue(param.getFlowInstanceId()));
        safeLabels(kubernetes.getLabels()).forEach(labels::putIfAbsent);
        return labels;
    }

    private Map<String, String> safeLabels(Map<String, String> labels) {
        Map<String, String> safe = new LinkedHashMap<>();
        labels.forEach((key, value) -> {
            if (!key.startsWith("datafusion.") && !key.startsWith("datafusion.io/")) {
                safe.put(key, SparkK8sNameGenerator.labelValue(value));
            }
        });
        return safe;
    }

    private Map<String, String> annotations(SparkKubernetesParam kubernetes) {
        Map<String, String> annotations = new LinkedHashMap<>(kubernetes.getAnnotations());
        annotations.put("datafusion.io/plugin-log-uri", pluginLogUri(kubernetes));
        annotations.put("datafusion.io/spark-web-ui-uri", kubernetes.getSparkWebUiUri());
        return annotations;
    }

    private String pluginLogUri(SparkKubernetesParam kubernetes) {
        if (!isBlank(kubernetes.getLogStorageUri())) {
            return kubernetes.getLogStorageUri();
        }
        return "spark-operator://" + kubernetes.getNamespace() + "/sparkapplications/"
                + kubernetes.getApplicationName();
    }

    private String listYaml(List<String> values, int indent) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append(spaces(indent)).append("- ").append(quote(value)).append(System.lineSeparator());
        }
        return builder.toString();
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

    private String literalBlock(String value, int indent) {
        StringBuilder builder = new StringBuilder();
        String text = value == null ? "" : value;
        for (String line : text.split("\\R", -1)) {
            builder.append(spaces(indent)).append(line).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String integerText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private String quote(String value) {
        return '"' + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + '"';
    }

    private String spaces(int count) {
        return " ".repeat(count);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
