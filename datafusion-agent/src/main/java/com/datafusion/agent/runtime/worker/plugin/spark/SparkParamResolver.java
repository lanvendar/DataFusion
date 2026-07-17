package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkK8sNameGenerator;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesParam;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesTemplateConstants;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spark 参数解析器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Component
public class SparkParamResolver {

    /**
     * 默认 Spark 版本.
     */
    private static final String DEFAULT_SPARK_VERSION = "4.0.2";

    /**
     * 默认 main class.
     */
    private static final String DEFAULT_MAIN_CLASS = "com.datafusion.plugin.spark.sql.SparkSqlApplication";

    /**
     * 默认命名空间.
     */
    private static final String DEFAULT_NAMESPACE = "default";

    /**
     * 默认镜像.
     */
    private static final String DEFAULT_IMAGE = "apache/spark:4.0.2-scala2.13-java17-ubuntu";

    /**
     * 默认镜像拉取策略.
     */
    private static final String DEFAULT_IMAGE_PULL_POLICY = "IfNotPresent";

    /**
     * 默认 Kubernetes 资源名称前缀.
     */
    private static final String DEFAULT_NAME_PREFIX = "df-spark";

    /**
     * 默认插件目录.
     */
    private static final String DEFAULT_PLUGIN_APP_DIR = "/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql";

    /**
     * 插件目录标识.
     */
    private static final String PLUGINS_PATH_SEGMENT = "/plugins/";

    /**
     * 默认插件 jar.
     */
    private static final String DEFAULT_PLUGIN_JAR_NAME = "plugin-spark-sql.jar";

    /**
     * 默认 jar 挂载目录.
     */
    private static final String DEFAULT_JAR_MOUNT_PATH = "/opt/spark/work-dir/datafusion-jars";

    /**
     * 默认 job 配置挂载目录.
     */
    private static final String DEFAULT_JOB_CONFIG_MOUNT_PATH = "/opt/datafusion/spark/jobs";

    /**
     * 默认 Web UI URI 模板.
     */
    private static final String DEFAULT_WEB_UI_TEMPLATE = "http://{{applicationName}}-ui-svc.{{namespace}}.svc:4040";

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析执行参数.
     *
     * @param snapshot    任务执行快照
     * @param workDirPath 固定任务运行目录
     * @return 执行参数
     */
    public SparkExecutionParam resolve(WorkerTaskExecutionSnap snapshot, String workDirPath) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot不能为空");
        }
        if (isBlank(workDirPath)) {
            throw new IllegalArgumentException("workDirPath不能为空");
        }
        JsonNode pluginParam = snapshot.getPluginParam();
        JsonNode taskData = snapshot.getTaskData();
        SparkRunMode runMode = SparkRunMode.parse(snapshot.getRunMode());
        JsonNode effectiveTaskData = effectiveTaskData(pluginParam, taskData);
        SparkExecutionParam param = SparkExecutionParam.builder()
                .runMode(runMode)
                .flowInstanceId(snapshot.getFlowInstanceId())
                .taskInstanceId(snapshot.getTaskInstanceId())
                .workDir(Path.of(workDirPath))
                .effectiveTaskData(effectiveTaskData)
                .sparkVersion(defaultText(text(pluginParam, "sparkVersion"), DEFAULT_SPARK_VERSION))
                .mainClass(defaultText(text(pluginParam, "mainClass"), DEFAULT_MAIN_CLASS))
                .sparkConf(mergeMap(object(pluginParam, "sparkConf"), object(taskData, "sparkConf")))
                .hadoopConf(mergeMap(object(pluginParam, "hadoopConf"), object(taskData, "hadoopConf")))
                .build();
        param.setKubernetes(resolveKubernetes(snapshot, pluginParam, taskData, param));
        param.setMainApplicationFile("local://" + param.getKubernetes().getJarMountPath()
                + "/" + param.getKubernetes().getPluginJarName());
        param.setArguments(arguments(param.getKubernetes()));
        validate(param);
        return param;
    }

    private SparkKubernetesParam resolveKubernetes(WorkerTaskExecutionSnap snapshot, JsonNode pluginParam,
            JsonNode taskData, SparkExecutionParam param) {
        JsonNode pluginKubernetes = object(pluginParam, "kubernetes");
        JsonNode taskKubernetes = object(taskData, "kubernetes");
        String namespace = firstText(text(taskKubernetes, "namespace"), text(pluginKubernetes, "namespace"),
                DEFAULT_NAMESPACE);
        String namePrefix = firstText(text(pluginKubernetes, "namePrefix"), DEFAULT_NAME_PREFIX);
        String applicationName = SparkK8sNameGenerator.applicationName(namePrefix, snapshot.getTaskInstanceId());
        String configMapName = SparkK8sNameGenerator.configMapName(namePrefix, snapshot.getTaskInstanceId());
        String webUiTemplate = firstText(text(taskKubernetes, "sparkWebUiUriTemplate"), DEFAULT_WEB_UI_TEMPLATE);
        String pluginAppDir = firstText(text(taskKubernetes, "pluginAppDir"), text(pluginKubernetes, "pluginAppDir"),
                DEFAULT_PLUGIN_APP_DIR);
        Map<String, String> nodeSelector = new LinkedHashMap<>();
        nodeSelector.put("kubernetes.io/arch", "amd64");
        nodeSelector.putAll(mergeMap(object(pluginKubernetes, "nodeSelector"), object(taskKubernetes, "nodeSelector")));
        return SparkKubernetesParam.builder()
                .namespace(namespace)
                .applicationName(applicationName)
                .configMapName(configMapName)
                .image(firstText(text(taskKubernetes, "image"), text(pluginKubernetes, "image"), DEFAULT_IMAGE))
                .imagePullPolicy(firstText(text(taskKubernetes, "imagePullPolicy"),
                        text(pluginKubernetes, "imagePullPolicy"), DEFAULT_IMAGE_PULL_POLICY))
                .serviceAccountName(firstText(text(taskKubernetes, "serviceAccountName"),
                        text(pluginKubernetes, "serviceAccountName")))
                .pluginAppDir(pluginAppDir)
                .sharedPvcName(firstText(text(taskKubernetes, "sharedPvcName"), text(pluginKubernetes, "sharedPvcName")))
                .sharedMountPath(sharedMountPath(pluginAppDir))
                .pluginJarName(firstText(text(taskKubernetes, "pluginJarName"), text(pluginKubernetes, "pluginJarName"),
                        DEFAULT_PLUGIN_JAR_NAME))
                .jarMountPath(firstText(text(taskKubernetes, "jarMountPath"), text(pluginKubernetes, "jarMountPath"),
                        DEFAULT_JAR_MOUNT_PATH))
                .jobConfigMountPath(firstText(text(taskKubernetes, "jobConfigMountPath"),
                        text(pluginKubernetes, "jobConfigMountPath"), DEFAULT_JOB_CONFIG_MOUNT_PATH))
                .podLabelSelector(SparkK8sNameGenerator.podLabelSelector(snapshot.getTaskInstanceId()))
                .logStorageUri(firstText(text(taskKubernetes, "logStorageUri"), text(pluginKubernetes, "logStorageUri")))
                .sparkWebUiUri(renderWebUiUri(webUiTemplate, namespace, applicationName))
                .collectLogsOnFinish(booleanValue(taskKubernetes, "collectLogsOnFinish",
                        booleanValue(pluginKubernetes, "collectLogsOnFinish", true)))
                .labels(mergeMap(object(pluginKubernetes, "labels"), object(taskKubernetes, "labels")))
                .annotations(mergeMap(object(pluginKubernetes, "annotations"), object(taskKubernetes, "annotations")))
                .nodeSelector(nodeSelector)
                .driver(firstNode(node(taskKubernetes, "driver"), node(pluginKubernetes, "driver")))
                .executor(firstNode(node(taskKubernetes, "executor"), node(pluginKubernetes, "executor")))
                .build();
    }

    private JsonNode effectiveTaskData(JsonNode pluginParam, JsonNode taskData) {
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        deepMerge(result, object(pluginParam, "defaultTaskData"));
        deepMerge(result, taskData);
        result.remove("kubernetes");
        return result;
    }

    private void deepMerge(ObjectNode target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        source.properties().forEach(entry -> {
            JsonNode current = target.get(entry.getKey());
            JsonNode value = entry.getValue();
            if (current instanceof ObjectNode currentObject && value != null && value.isObject()) {
                deepMerge(currentObject, value);
            } else {
                target.set(entry.getKey(), value);
            }
        });
    }

    private void validate(SparkExecutionParam param) {
        if (param.getRunMode() != SparkRunMode.K8S_OPERATOR) {
            throw new IllegalArgumentException("Spark首版仅支持K8S_OPERATOR");
        }
        if (!DEFAULT_SPARK_VERSION.equals(param.getSparkVersion())) {
            throw new IllegalArgumentException("暂不支持的Spark版本: " + param.getSparkVersion());
        }
        SparkKubernetesParam kubernetes = param.getKubernetes();
        if (isBlank(kubernetes.getImage())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.image不能为空");
        }
        if (isBlank(kubernetes.getServiceAccountName())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.serviceAccountName不能为空");
        }
        if (isBlank(kubernetes.getPluginAppDir())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.pluginAppDir不能为空");
        }
        if (isBlank(kubernetes.getSharedPvcName())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.sharedPvcName不能为空");
        }
        if (isBlank(kubernetes.getPluginJarName())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.pluginJarName不能为空");
        }
    }

    private List<String> arguments(SparkKubernetesParam kubernetes) {
        return new ArrayList<>(List.of("--job-file",
                kubernetes.getJobConfigMountPath() + "/" + SparkKubernetesTemplateConstants.JOB_JSON_FILE_NAME));
    }

    private String renderWebUiUri(String template, String namespace, String applicationName) {
        return template.replace("{{namespace}}", namespace).replace("{{applicationName}}", applicationName);
    }

    private String sharedMountPath(String pluginAppDir) {
        String normalized = pluginAppDir.replaceAll("/+$", "");
        int pluginsIndex = normalized.indexOf(PLUGINS_PATH_SEGMENT);
        if (pluginsIndex >= 0) {
            return pluginsIndex == 0 ? "/" : normalized.substring(0, pluginsIndex);
        }
        Path parent = Path.of(normalized).getParent();
        return parent == null ? "/" : parent.toString();
    }

    private JsonNode firstNode(JsonNode first, JsonNode second) {
        return first == null || first.isNull() ? second : first;
    }

    private JsonNode node(JsonNode node, String fieldName) {
        return node == null ? null : node.get(fieldName);
    }

    private JsonNode object(JsonNode node, String fieldName) {
        JsonNode value = node(node, fieldName);
        return value != null && value.isObject() ? value : OBJECT_MAPPER.createObjectNode();
    }

    private Map<String, String> mergeMap(JsonNode first, JsonNode second) {
        Map<String, String> result = new LinkedHashMap<>();
        appendMap(result, first);
        appendMap(result, second);
        return result;
    }

    private void appendMap(Map<String, String> target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        source.properties().forEach(entry -> target.put(entry.getKey(), entry.getValue().asText()));
    }

    private boolean booleanValue(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode value = node(node, fieldName);
        return value == null || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node(node, fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
