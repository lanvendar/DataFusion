package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkK8sNameGenerator;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesParam;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesTemplateConstants;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flink parameter resolver.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Component
public class FlinkParamResolver {

    /**
     * Date formatter.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Default launch mode.
     */
    private static final String DEFAULT_LAUNCH_MODE = "JAR";

    /**
     * Default Flink version.
     */
    private static final String DEFAULT_FLINK_VERSION = "2.2.0";

    /**
     * Default lib dir.
     */
    private static final String DEFAULT_LIB_DIR = "lib";

    /**
     * Default Kubernetes namespace.
     */
    private static final String DEFAULT_K8S_NAMESPACE = "default";

    /**
     * Default Flink image.
     */
    private static final String DEFAULT_FLINK_IMAGE = "flink:2.2.0-scala_2.12-java17";

    /**
     * Default image pull policy.
     */
    private static final String DEFAULT_IMAGE_PULL_POLICY = "IfNotPresent";

    /**
     * Default deployment prefix.
     */
    private static final String DEFAULT_DEPLOYMENT_NAME_PREFIX = "df-flink-";

    /**
     * Default Secret prefix.
     */
    private static final String DEFAULT_SECRET_NAME_PREFIX = "df-flink-job-";

    /**
     * Default upgrade mode.
     */
    private static final String DEFAULT_UPGRADE_MODE = "stateless";

    /**
     * Default web UI template.
     */
    private static final String DEFAULT_WEB_UI_TEMPLATE = "http://{{deploymentName}}-rest.{{namespace}}.svc:8081";

    /**
     * Default args.
     */
    private static final List<String> DEFAULT_ARGS = List.of("--config", "{{jobJsonMountPath}}");

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Agent properties.
     */
    private final AgentProperties properties;

    /**
     * Constructor.
     *
     * @param properties agent properties
     */
    public FlinkParamResolver(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolve execution param.
     *
     * @param request task request
     * @return execution parameter
     */
    public FlinkExecutionParam resolve(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        JsonNode taskData = request.getTaskData();
        JsonNode pluginParam = request.getPluginParam();
        FlinkRunMode runMode = FlinkRunMode.parse(text(pluginParam, "runMode"));
        Path runtimeDir = taskRuntimeDir(LocalDate.now().format(DATE_FORMATTER), request);
        JsonNode effectiveTaskData = effectiveTaskData(pluginParam, taskData);
        Map<String, String> flinkConfig = effectiveFlinkConfig(pluginParam, effectiveTaskData);
        writeFlinkConfig(effectiveTaskData, flinkConfig);
        List<String> args = resolvedArgs(taskData, pluginParam);
        FlinkExecutionParam param = FlinkExecutionParam.builder()
                .runMode(runMode)
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .jobJson(jobJson(taskData))
                .effectiveTaskData(effectiveTaskData)
                .workDir(runtimeDir)
                .flinkConfig(flinkConfig)
                .flinkAppDir(text(pluginParam, "flinkAppDir"))
                .launchMode(defaultText(text(pluginParam, "launchMode"), DEFAULT_LAUNCH_MODE))
                .flinkAppJar(text(pluginParam, "flinkAppJar"))
                .classpath(defaultText(text(pluginParam, "classpath"), ""))
                .mainClass(text(pluginParam, "mainClass"))
                .flinkVersion(defaultText(text(pluginParam, "flinkVersion"), DEFAULT_FLINK_VERSION))
                .libDir(defaultText(text(pluginParam, "libDir"), DEFAULT_LIB_DIR))
                .args(args)
                .build();
        param.setKubernetes(runMode == FlinkRunMode.K8S_OPERATOR ? resolveKubernetes(request, taskData, pluginParam,
                param, args) : null);
        validate(param);
        return param;
    }

    private FlinkKubernetesParam resolveKubernetes(TaskRequest request, JsonNode taskData, JsonNode pluginParam,
            FlinkExecutionParam param, List<String> args) {
        JsonNode pluginKubernetes = object(pluginParam, "kubernetes");
        JsonNode taskKubernetes = object(taskData, "kubernetes");
        String deploymentPrefix = firstText(text(taskKubernetes, "deploymentNamePrefix"),
                text(pluginKubernetes, "deploymentNamePrefix"), DEFAULT_DEPLOYMENT_NAME_PREFIX);
        String secretPrefix = firstText(text(taskKubernetes, "secretNamePrefix"),
                text(pluginKubernetes, "secretNamePrefix"), DEFAULT_SECRET_NAME_PREFIX);
        String deploymentName = FlinkK8sNameGenerator.deploymentName(deploymentPrefix, request.getTaskInstanceId());
        String secretName = FlinkK8sNameGenerator.secretName(secretPrefix, request.getTaskInstanceId());
        String namespace = firstText(text(taskKubernetes, "namespace"), text(pluginKubernetes, "namespace"),
                DEFAULT_K8S_NAMESPACE);
        String flinkAppDir = param.getFlinkAppDir();
        String jarUri = "local://" + joinPath(FlinkKubernetesTemplateConstants.USRLIB_PATH, param.getFlinkAppJar());
        String webUiTemplate = firstText(text(taskKubernetes, "flinkWebUiUriTemplate"),
                text(pluginKubernetes, "flinkWebUiUriTemplate"), DEFAULT_WEB_UI_TEMPLATE);
        Map<String, String> nodeSelector = new LinkedHashMap<>();
        nodeSelector.put("kubernetes.io/arch", "amd64");
        nodeSelector.putAll(mergeMap(object(pluginKubernetes, "nodeSelector"), object(taskKubernetes, "nodeSelector")));
        return FlinkKubernetesParam.builder()
                .namespace(namespace)
                .image(firstText(text(taskKubernetes, "image"), text(pluginKubernetes, "image"), DEFAULT_FLINK_IMAGE))
                .imagePullPolicy(firstText(text(taskKubernetes, "imagePullPolicy"),
                        text(pluginKubernetes, "imagePullPolicy"), DEFAULT_IMAGE_PULL_POLICY))
                .serviceAccountName(firstText(text(taskKubernetes, "serviceAccountName"),
                        text(pluginKubernetes, "serviceAccountName")))
                .sharedPvcName(firstText(text(taskKubernetes, "sharedPvcName"), text(pluginKubernetes, "sharedPvcName")))
                .sharedMountPath(sharedMountPath(flinkAppDir))
                .deploymentName(deploymentName)
                .secretName(secretName)
                .flinkWebUiUri(renderWebUiUri(webUiTemplate, namespace, deploymentName))
                .upgradeMode(firstText(text(taskKubernetes, "upgradeMode"), text(pluginKubernetes, "upgradeMode"),
                        DEFAULT_UPGRADE_MODE))
                .logStorageUri(firstText(text(taskKubernetes, "logStorageUri"), text(pluginKubernetes, "logStorageUri")))
                .collectLogsOnFinish(booleanValue(taskKubernetes, "collectLogsOnFinish",
                        booleanValue(pluginKubernetes, "collectLogsOnFinish", true)))
                .deleteDeploymentOnFinish(booleanValue(taskKubernetes, "deleteDeploymentOnFinish",
                        booleanValue(pluginKubernetes, "deleteDeploymentOnFinish", false)))
                .deleteSecretOnFinish(booleanValue(taskKubernetes, "deleteSecretOnFinish",
                        booleanValue(pluginKubernetes, "deleteSecretOnFinish", true)))
                .labels(mergeMap(object(pluginKubernetes, "labels"), object(taskKubernetes, "labels")))
                .annotations(mergeMap(object(pluginKubernetes, "annotations"), object(taskKubernetes, "annotations")))
                .env(mergeMap(object(pluginKubernetes, "env"), object(taskKubernetes, "env")))
                .nodeSelector(nodeSelector)
                .jobManagerResource(resource(object(pluginKubernetes, "jobManager"), object(taskKubernetes, "jobManager")))
                .taskManagerResource(resource(object(pluginKubernetes, "taskManager"),
                        object(taskKubernetes, "taskManager")))
                .podLabelSelector(FlinkK8sNameGenerator.podLabelSelector(request.getTaskInstanceId()))
                .flinkAppDir(flinkAppDir)
                .jarUri(jarUri)
                .jobJsonMountPath(FlinkKubernetesTemplateConstants.JOB_JSON_MOUNT_PATH)
                .args(replaceArgs(args))
                .build();
    }

    private void validate(FlinkExecutionParam param) {
        if (param.getRunMode() != FlinkRunMode.K8S_OPERATOR) {
            return;
        }
        require(param.getFlinkAppDir(), "pluginParam.flinkAppDir不能为空");
        require(param.getFlinkAppJar(), "pluginParam.flinkAppJar不能为空");
        require(param.getMainClass(), "pluginParam.mainClass不能为空");
        require(param.getKubernetes().getImage(), "pluginParam.kubernetes.image或taskData.kubernetes.image不能为空");
        require(param.getKubernetes().getSharedPvcName(), "pluginParam.kubernetes.sharedPvcName不能为空");
        if (!DEFAULT_FLINK_VERSION.equals(param.getFlinkVersion())) {
            throw new IllegalArgumentException("首版仅支持pluginParam.flinkVersion=2.2.0");
        }
        if (!DEFAULT_LAUNCH_MODE.equalsIgnoreCase(param.getLaunchMode())) {
            throw new IllegalArgumentException("首版仅支持pluginParam.launchMode=JAR");
        }
    }

    private JsonNode effectiveTaskData(JsonNode pluginParam, JsonNode taskData) {
        JsonNode jobJson = jobJson(taskData);
        if (jobJson != null) {
            return parseJobJson(jobJson);
        }
        JsonNode defaultTaskData = object(pluginParam, "defaultTaskData");
        ObjectNode result = defaultTaskData == null ? OBJECT_MAPPER.createObjectNode() : defaultTaskData.deepCopy();
        JsonNode overrideTaskData = taskDataOverride(taskData);
        if (overrideTaskData != null && overrideTaskData.isObject()) {
            deepMerge(result, overrideTaskData);
        }
        return result;
    }

    private JsonNode parseJobJson(JsonNode jobJson) {
        if (jobJson == null || jobJson.isNull()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        if (jobJson.isObject()) {
            return jobJson.deepCopy();
        }
        if (jobJson.isTextual()) {
            try {
                return OBJECT_MAPPER.readTree(jobJson.asText());
            } catch (Exception e) {
                throw new IllegalArgumentException("taskData.jobJson不是合法JSON", e);
            }
        }
        throw new IllegalArgumentException("taskData.jobJson必须是Object或JSON字符串");
    }

    private JsonNode taskDataOverride(JsonNode taskData) {
        if (taskData == null || !taskData.isObject()) {
            return null;
        }
        ObjectNode override = taskData.deepCopy();
        override.remove(List.of("jobJson", "jobName", "args", "kubernetes", "pluginLogUri", "data", "options"));
        return override;
    }

    private Map<String, String> effectiveFlinkConfig(JsonNode pluginParam, JsonNode effectiveTaskData) {
        Map<String, String> map = mergeMap(object(pluginParam, "flinkConfig"));
        map.putAll(mergeMap(object(effectiveTaskData, "flinkConfig")));
        return map;
    }

    private void writeFlinkConfig(JsonNode effectiveTaskData, Map<String, String> flinkConfig) {
        if (effectiveTaskData instanceof ObjectNode objectNode) {
            ObjectNode config = OBJECT_MAPPER.createObjectNode();
            flinkConfig.forEach(config::put);
            objectNode.set("flinkConfig", config);
        }
    }

    private JsonNode jobJson(JsonNode taskData) {
        return taskData == null ? null : taskData.get("jobJson");
    }

    private JsonNode object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject() ? value : null;
    }

    private JsonNode resource(JsonNode pluginValue, JsonNode taskValue) {
        return firstNode(taskValue == null ? null : taskValue.get("resource"),
                pluginValue == null ? null : pluginValue.get("resource"));
    }

    private JsonNode firstNode(JsonNode first, JsonNode second) {
        if (first != null && !first.isNull()) {
            return first;
        }
        return second;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isBoolean() ? defaultValue : value.asBoolean();
    }

    private List<String> resolvedArgs(JsonNode taskData, JsonNode pluginParam) {
        JsonNode taskArgs = taskData == null ? null : taskData.get("args");
        if (taskArgs != null && taskArgs.isArray()) {
            return list(taskArgs);
        }
        JsonNode pluginArgs = pluginParam == null ? null : pluginParam.get("args");
        return pluginArgs != null && pluginArgs.isArray() ? list(pluginArgs) : new ArrayList<>(DEFAULT_ARGS);
    }

    private List<String> list(JsonNode value) {
        List<String> list = new ArrayList<>();
        value.forEach(item -> list.add(item.asText()));
        return list;
    }

    private List<String> replaceArgs(List<String> args) {
        List<String> result = new ArrayList<>();
        for (String arg : args) {
            result.add(arg == null ? "" : arg.replace("{{jobJsonMountPath}}",
                    FlinkKubernetesTemplateConstants.JOB_JSON_MOUNT_PATH));
        }
        return result;
    }

    private Map<String, String> mergeMap(JsonNode... nodes) {
        Map<String, String> map = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            if (node != null && node.isObject()) {
                node.properties().forEach(entry -> map.put(entry.getKey(), entry.getValue().asText()));
            }
        }
        return map;
    }

    private void deepMerge(ObjectNode target, JsonNode override) {
        override.properties().forEach(entry -> {
            JsonNode current = target.get(entry.getKey());
            JsonNode next = entry.getValue();
            if (current != null && current.isObject() && next != null && next.isObject()) {
                deepMerge((ObjectNode) current, next);
            } else {
                target.set(entry.getKey(), next);
            }
        });
    }

    private String renderWebUiUri(String template, String namespace, String deploymentName) {
        return template.replace("{{namespace}}", namespace)
                .replace("{{deploymentName}}", deploymentName)
                .replace("{{appId}}", deploymentName);
    }

    private String joinPath(String first, String... parts) {
        String result = trimRight(first);
        for (String part : parts) {
            result = result + "/" + trimBoth(part);
        }
        return result;
    }

    private String parentPath(String path) {
        String normalized = trimRight(path);
        int index = normalized.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return normalized.substring(0, index);
    }

    private String sharedMountPath(String flinkAppDir) {
        String normalized = trimRight(flinkAppDir);
        int pluginsIndex = normalized.indexOf("/plugins/");
        if (pluginsIndex > 0) {
            return normalized.substring(0, pluginsIndex);
        }
        if (pluginsIndex == 0) {
            return "/";
        }
        return parentPath(normalized);
    }

    private String trimRight(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private String trimBoth(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String firstText(String... values) {
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

    private void require(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safePath(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    private Path taskRuntimeDir(String date, TaskRequest request) {
        return Path.of(properties.getStorage().getTaskRuntimeDir(), date, safePath(request.getFlowInstanceId()),
                safePath(request.getTaskInstanceId()));
    }
}
