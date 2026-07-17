package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxK8sNameGenerator;
import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesParam;
import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesTemplateConstants;
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
 * DataX parameter resolver.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
public class DataxParamResolver {

    /**
     * Local DataX log file name.
     */
    private static final String LOCAL_DATAX_LOG_FILE_NAME = "local-datax.log";

    /**
     * Default Java binary.
     */
    private static final String DEFAULT_JAVA_BIN = "java";

    /**
     * Default DataX main class.
     */
    private static final String DEFAULT_MAIN_CLASS = "com.alibaba.datax.core.Engine";

    /**
     * Default DataX job mode.
     */
    private static final String DEFAULT_JOB_MODE = "standalone";

    /**
     * Default DataX job ID.
     */
    private static final String DEFAULT_JOB_ID = "-1";

    /**
     * Default JVM options.
     */
    private static final List<String> DEFAULT_JVM_OPTIONS = List.of(
            "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8",
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
    private static final String DEFAULT_WRITE_JOB_FILE_PERMISSIONS = "OWNER_READ,OWNER_WRITE,OWNER_EXECUTE,GROUP_READ,"
            + "GROUP_EXECUTE,OTHERS_READ,OTHERS_EXECUTE";

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
     * Default Kubernetes resource name prefix.
     */
    private static final String DEFAULT_NAME_PREFIX = "df-datax";

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Resolve execution param.
     *
     * @param snapshot task execution snapshot
     * @param workDirPath fixed task work directory
     * @return execution param
     */
    public DataxExecutionParam resolve(WorkerTaskExecutionSnap snapshot, String workDirPath) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot不能为空");
        }
        if (isBlank(workDirPath)) {
            throw new IllegalArgumentException("workDirPath不能为空");
        }
        JsonNode taskData = snapshot.getTaskData();
        JsonNode pluginParam = snapshot.getPluginParam();
        DataxRunMode runMode = DataxRunMode.parse(snapshot.getRunMode());
        Path runtimeDir = Path.of(workDirPath);
        JsonNode effectiveTaskData = effectiveTaskData(pluginParam, taskData);
        String dataxHome = text(pluginParam, "dataxHome");
        String dataxJar = text(pluginParam, "dataxJar");
        if (isBlank(dataxJar) && !isBlank(dataxHome)) {
            dataxJar = Path.of(dataxHome, "lib", "datax-bundle-0.0.1.jar").toString();
        }
        String logbackConfigFile = text(pluginParam, "logConfigFile");
        if (isBlank(logbackConfigFile) && !isBlank(dataxHome)) {
            logbackConfigFile = Path.of(dataxHome, "conf", "logback.xml").toString();
        }
        validateJobSource(pluginParam, taskData, effectiveTaskData, runMode);
        DataxKubernetesParam kubernetes = runMode == DataxRunMode.K8S ? resolveKubernetes(snapshot, taskData,
                pluginParam) : null;
        validateKubernetes(kubernetes);
        return DataxExecutionParam.builder()
                .runMode(runMode)
                .taskInstanceId(snapshot.getTaskInstanceId())
                .flowInstanceId(snapshot.getFlowInstanceId())
                .jobJson(jobJson(taskData))
                .effectiveTaskData(effectiveTaskData)
                .jobFile(text(pluginParam, "jobFile"))
                .workDir(runtimeDir)
                .dataxHome(dataxHome)
                .dataxJar(dataxJar)
                .logbackConfigFile(logbackConfigFile)
                .mainClass(defaultText(text(pluginParam, "mainClass"), DEFAULT_MAIN_CLASS))
                .jobMode(defaultText(text(pluginParam, "jobMode"), DEFAULT_JOB_MODE))
                .jobId(defaultText(text(pluginParam, "jobId"), DEFAULT_JOB_ID))
                .javaBin(defaultText(text(pluginParam, "javaBin"), DEFAULT_JAVA_BIN))
                .logFile(runtimeDir.resolve(LOCAL_DATAX_LOG_FILE_NAME))
                .logLevel(defaultText(text(pluginParam, "logLevel"), DEFAULT_LOG_LEVEL))
                .logMaxSize(defaultText(text(pluginParam, "logMaxSize"), DEFAULT_LOG_MAX_SIZE))
                .logMaxIndex(intValue(pluginParam, "logMaxIndex", DEFAULT_LOG_MAX_INDEX))
                .writeJobFilePermissions(defaultText(text(pluginParam, "writeJobFilePermissions"),
                        DEFAULT_WRITE_JOB_FILE_PERMISSIONS))
                .jvmOptions(listOrDefault(pluginParam, "jvmOptions", DEFAULT_JVM_OPTIONS))
                .kubernetes(kubernetes)
                .build();
    }

    private DataxKubernetesParam resolveKubernetes(WorkerTaskExecutionSnap snapshot, JsonNode taskData,
            JsonNode pluginParam) {
        JsonNode pluginKubernetes = object(pluginParam, "kubernetes");
        JsonNode taskKubernetes = object(taskData, "kubernetes");
        String namePrefix = firstText(text(pluginKubernetes, "namePrefix"), DEFAULT_NAME_PREFIX);
        String jobName = DataxK8sNameGenerator.jobName(namePrefix, snapshot.getTaskInstanceId());
        String secretName = DataxK8sNameGenerator.secretName(namePrefix, snapshot.getTaskInstanceId());
        String namespace = firstText(text(taskKubernetes, "namespace"), text(pluginKubernetes, "namespace"),
                DEFAULT_K8S_NAMESPACE);
        Map<String, String> labels = mergeMap(object(pluginKubernetes, "labels"), object(taskKubernetes, "labels"));
        Map<String, String> annotations = mergeMap(object(pluginKubernetes, "annotations"),
                object(taskKubernetes, "annotations"));
        Map<String, String> env = mergeMap(object(pluginKubernetes, "env"), object(taskKubernetes, "env"));
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
                .podLabelSelector(DataxK8sNameGenerator.podLabelSelector(snapshot.getTaskInstanceId()))
                .build();
    }

    private void validateJobSource(JsonNode pluginParam, JsonNode taskData, JsonNode effectiveTaskData,
            DataxRunMode runMode) {
        if (!isBlank(text(pluginParam, "jobFile")) || jobJson(taskData) != null || !isEmptyObject(effectiveTaskData)) {
            return;
        }
        throw new IllegalArgumentException("pluginParam.jobFile、pluginParam.defaultTaskData、taskData.jobJson、taskData至少配置一个");
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

    private List<String> listOrDefault(JsonNode node, String field, List<String> defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) {
            return new ArrayList<>(defaultValue);
        }
        return list(node, field);
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

    private JsonNode effectiveTaskData(JsonNode pluginParam, JsonNode taskData) {
        JsonNode defaultTaskData = object(pluginParam, "defaultTaskData");
        ObjectNode result = defaultTaskData == null ? OBJECT_MAPPER.createObjectNode() : defaultTaskData.deepCopy();
        JsonNode overrideTaskData = taskDataOverride(taskData);
        if (overrideTaskData != null && overrideTaskData.isObject()) {
            deepMerge(result, overrideTaskData);
        }
        return result;
    }

    private JsonNode taskDataOverride(JsonNode taskData) {
        if (taskData == null || !taskData.isObject()) {
            return null;
        }
        ObjectNode override = taskData.deepCopy();
        override.remove(List.of("jobJson", "jobName", "jobPath", "jobFileName", "env", "jvmOptions", "dataxArgs",
                "kubernetes", "pluginLogUri", "data", "options"));
        return override;
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

    private boolean isEmptyObject(JsonNode node) {
        return node == null || node.isObject() && node.isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
