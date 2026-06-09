package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxK8sNameGenerator;
import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesParam;
import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesTemplateConstants;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataX parameter resolver.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class DataxParamResolver {

    /**
     * Date formatter.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * DataX work directory.
     */
    private static final String WORK_DIR = "datax-work";

    /**
     * Default Java binary.
     */
    private static final String DEFAULT_JAVA_BIN = "java";

    /**
     * Default JVM options.
     */
    private static final List<String> DEFAULT_JVM_OPTIONS = List.of(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED");

    /**
     * Default log level.
     */
    private static final String DEFAULT_LOG_LEVEL = "INFO";

    /**
     * Default log max size.
     */
    private static final String DEFAULT_LOG_MAX_SIZE = "100MB";

    /**
     * Default log max index.
     */
    private static final int DEFAULT_LOG_MAX_INDEX = 100;

    /**
     * Default generated job file permissions.
     */
    private static final String DEFAULT_WRITE_JOB_FILE_PERMISSIONS = "OWNER_READ,OWNER_WRITE";

    /**
     * Default Kubernetes namespace.
     */
    private static final String DEFAULT_K8S_NAMESPACE = "default";

    /**
     * Default Kubernetes image pull policy.
     */
    private static final String DEFAULT_IMAGE_PULL_POLICY = "IfNotPresent";

    /**
     * Default Kubernetes Job backoff limit.
     */
    private static final int DEFAULT_BACKOFF_LIMIT = 0;

    /**
     * Default Kubernetes Job ttl seconds after finished.
     */
    private static final int DEFAULT_TTL_SECONDS_AFTER_FINISHED = 86400;

    /**
     * Default Kubernetes Job name prefix.
     */
    private static final String DEFAULT_JOB_NAME_PREFIX = "df-datax-";

    /**
     * Default Kubernetes Secret name prefix.
     */
    private static final String DEFAULT_SECRET_NAME_PREFIX = "df-datax-job-";

    /**
     * Agent properties.
     */
    private final AgentProperties properties;

    /**
     * Constructor.
     *
     * @param properties agent properties
     */
    public DataxParamResolver(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolve execution param.
     *
     * @param request task request
     * @return execution param
     */
    public DataxExecutionParam resolve(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request不能为空");
        }
        JsonNode taskData = request.getTaskData();
        JsonNode pluginParam = request.getPluginParam();
        DataxRunMode runMode = DataxRunMode.parse(text(pluginParam, "runMode"));
        String date = LocalDate.now().format(DATE_FORMATTER);
        Path logDir = Path.of(properties.getModules(), properties.getStorage().getLogsDir(), date,
                safePath(request.getFlowInstanceId()), safePath(request.getTaskInstanceId()));
        Path workDir = Path.of(properties.getModules(), WORK_DIR, date,
                safePath(request.getFlowInstanceId()), safePath(request.getTaskInstanceId()));
        String jobName = defaultText(text(taskData, "jobName"), request.getTaskInstanceId() + ".json");
        String resourcesRoot = text(pluginParam, "resourcesRoot");
        String dataxHome = text(pluginParam, "dataxHome");
        if (isBlank(dataxHome) && !isBlank(resourcesRoot)) {
            dataxHome = Path.of(resourcesRoot, "datax").toString();
        }
        String dataxJar = text(pluginParam, "dataxJar");
        if (isBlank(dataxJar) && !isBlank(dataxHome)) {
            dataxJar = Path.of(dataxHome, "lib", "datax-bundle-0.0.1.jar").toString();
        }
        validateJobSource(taskData, runMode);
        DataxKubernetesParam kubernetes = runMode == DataxRunMode.K8S ? resolveKubernetes(request, taskData,
                pluginParam) : null;
        validateKubernetes(kubernetes);
        return DataxExecutionParam.builder()
                .runMode(runMode)
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .jobName(jobName)
                .jobJson(jobJson(taskData))
                .pluginParam(pluginParam)
                .jobFileName(text(taskData, "jobFileName"))
                .jobPath(text(taskData, "jobPath"))
                .logDir(logDir)
                .workDir(workDir)
                .resourcesRoot(resourcesRoot)
                .dataxHome(dataxHome)
                .dataxJar(dataxJar)
                .javaBin(defaultText(text(pluginParam, "javaBin"), DEFAULT_JAVA_BIN))
                .logFile(logDir.resolve(jobName + ".log"))
                .logLevel(defaultText(text(pluginParam, "logLevel"), DEFAULT_LOG_LEVEL))
                .logMaxSize(defaultText(text(pluginParam, "logMaxSize"), DEFAULT_LOG_MAX_SIZE))
                .logMaxIndex(intValue(pluginParam, "logMaxIndex", DEFAULT_LOG_MAX_INDEX))
                .writeJobFilePermissions(defaultText(text(pluginParam, "writeJobFilePermissions"),
                        DEFAULT_WRITE_JOB_FILE_PERMISSIONS))
                .env(mergeMap(object(pluginParam, "env"), object(taskData, "env")))
                .jvmOptions(mergeList(DEFAULT_JVM_OPTIONS, list(pluginParam, "jvmOptions"), list(taskData, "jvmOptions")))
                .dataxArgs(list(taskData, "dataxArgs"))
                .kubernetes(kubernetes)
                .build();
    }

    private DataxKubernetesParam resolveKubernetes(TaskRequest request, JsonNode taskData, JsonNode pluginParam) {
        JsonNode pluginKubernetes = object(pluginParam, "kubernetes");
        JsonNode taskKubernetes = object(taskData, "kubernetes");
        String jobNamePrefix = firstText(text(taskKubernetes, "jobNamePrefix"),
                text(pluginKubernetes, "jobNamePrefix"), DEFAULT_JOB_NAME_PREFIX);
        String secretNamePrefix = firstText(text(taskKubernetes, "secretNamePrefix"),
                text(pluginKubernetes, "secretNamePrefix"), DEFAULT_SECRET_NAME_PREFIX);
        String jobName = DataxK8sNameGenerator.jobName(jobNamePrefix, request.getTaskInstanceId());
        String secretName = DataxK8sNameGenerator.secretName(secretNamePrefix, request.getTaskInstanceId());
        String namespace = firstText(text(taskKubernetes, "namespace"), text(pluginKubernetes, "namespace"),
                DEFAULT_K8S_NAMESPACE);
        Map<String, String> labels = mergeMap(object(pluginKubernetes, "labels"), object(taskKubernetes, "labels"));
        Map<String, String> annotations = mergeMap(object(pluginKubernetes, "annotations"),
                object(taskKubernetes, "annotations"));
        Map<String, String> env = mergeMap(object(pluginParam, "env"), object(pluginKubernetes, "env"),
                object(taskData, "env"), object(taskKubernetes, "env"));
        return DataxKubernetesParam.builder()
                .namespace(namespace)
                .image(firstText(text(taskKubernetes, "image"), text(pluginKubernetes, "image")))
                .imagePullPolicy(firstText(text(taskKubernetes, "imagePullPolicy"),
                        text(pluginKubernetes, "imagePullPolicy"), DEFAULT_IMAGE_PULL_POLICY))
                .serviceAccountName(firstText(text(taskKubernetes, "serviceAccountName"),
                        text(pluginKubernetes, "serviceAccountName")))
                .backoffLimit(intValue(taskKubernetes, "backoffLimit",
                        intValue(pluginKubernetes, "backoffLimit", DEFAULT_BACKOFF_LIMIT)))
                .activeDeadlineSeconds(longValue(taskKubernetes, "activeDeadlineSeconds",
                        longValue(pluginKubernetes, "activeDeadlineSeconds", null)))
                .ttlSecondsAfterFinished(integerValue(taskKubernetes, "ttlSecondsAfterFinished",
                        integerValue(pluginKubernetes, "ttlSecondsAfterFinished", DEFAULT_TTL_SECONDS_AFTER_FINISHED)))
                .jobName(jobName)
                .secretName(secretName)
                .jobJsonMountPath(DataxKubernetesTemplateConstants.JOB_JSON_MOUNT_PATH)
                .dataxHome(DataxKubernetesTemplateConstants.DATAX_HOME)
                .containerName(DataxKubernetesTemplateConstants.CONTAINER_NAME)
                .logStorageUri(firstText(text(taskKubernetes, "logStorageUri"),
                        text(pluginKubernetes, "logStorageUri")))
                .collectLogsOnFinish(booleanValue(taskKubernetes, "collectLogsOnFinish",
                        booleanValue(pluginKubernetes, "collectLogsOnFinish", true)))
                .deleteJobOnFinish(booleanValue(taskKubernetes, "deleteJobOnFinish",
                        booleanValue(pluginKubernetes, "deleteJobOnFinish", false)))
                .labels(labels)
                .annotations(annotations)
                .env(env)
                .nodeSelector(mergeMap(object(pluginKubernetes, "nodeSelector"), object(taskKubernetes, "nodeSelector")))
                .resources(firstNode(taskKubernetes == null ? null : taskKubernetes.get("resources"),
                        pluginKubernetes == null ? null : pluginKubernetes.get("resources")))
                .podLabelSelector(DataxK8sNameGenerator.podLabelSelector(request.getTaskInstanceId()))
                .build();
    }

    private void validateJobSource(JsonNode taskData, DataxRunMode runMode) {
        if (jobJson(taskData) == null && isBlank(text(taskData, "jobPath")) && isBlank(text(taskData, "jobFileName"))) {
            throw new IllegalArgumentException("taskData.jobJson、taskData.jobPath、taskData.jobFileName至少配置一个");
        }
        if (runMode == DataxRunMode.K8S && !isBlank(text(taskData, "jobPath"))) {
            throw new IllegalArgumentException("taskData.jobPath仅支持LOCAL运行模式");
        }
    }

    private void validateKubernetes(DataxKubernetesParam kubernetes) {
        if (kubernetes != null && isBlank(kubernetes.getImage())) {
            throw new IllegalArgumentException("pluginParam.kubernetes.image或taskData.kubernetes.image不能为空");
        }
    }

    private JsonNode jobJson(JsonNode taskData) {
        if (taskData == null) {
            return null;
        }
        return taskData.get("jobJson");
    }

    private JsonNode object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject() ? value : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? defaultValue : value.asInt();
    }

    private Integer integerValue(JsonNode node, String field, Integer defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? defaultValue : value.asInt();
    }

    private Long longValue(JsonNode node, String field, Long defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? defaultValue : Long.valueOf(value.asLong());
    }

    private boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isBoolean() ? defaultValue : value.asBoolean();
    }

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        List<String> list = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> list.add(item.asText()));
        }
        return list;
    }

    private List<String> mergeList(List<String> first, List<String> second, List<String> third) {
        List<String> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        result.addAll(second);
        result.addAll(third);
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

    private JsonNode firstNode(JsonNode first, JsonNode second) {
        if (first != null && !first.isNull()) {
            return first;
        }
        return second;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safePath(String value) {
        return isBlank(value) ? "unknown" : value;
    }
}
