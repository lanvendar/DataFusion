package com.datafusion.agent.runtime.worker.plugin.api.local;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.template.LocalProcessSpec;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateYamlFragments;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 本地插件执行器.
 *
 * <p>
 * 第一版只支持 LOCAL 运行模式，将 API 插件参数转换为 Shell LOCAL 的 java 命令后提交.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/2
 * @since 1.0.0
 */
@Component
public class ApiLocalPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * 插件类型.
     */
    public static final String PLUGIN_TYPE = "API";

    /**
     * 日期格式.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * API job file name.
     */
    private static final String JOB_FILE_NAME = "api-job.json";

    /**
     * 默认 Java 命令.
     */
    private static final String DEFAULT_JAVA_BIN = "java";

    /**
     * 默认 API 插件 jar 路径.
     */
    private static final String DEFAULT_API_JAR =
            "/opt/datafusion/plugins/api/datafusion-plugin-api-1.0.0-executable.jar";

    /**
     * 默认启动主类.
     */
    private static final String DEFAULT_MAIN_CLASS = "com.datafusion.plugin.api.ApiExtractApplication";

    /**
     * 默认日志配置路径.
     */
    private static final String DEFAULT_LOG_CONFIG_FILE = "/opt/datafusion/plugins/api/conf/logback.xml";

    /**
     * 默认日志级别.
     */
    private static final String DEFAULT_LOG_LEVEL = "INFO";

    /**
     * 默认日志目录.
     */
    private static final String DEFAULT_LOG_HOME = "logs";

    /**
     * 默认日志文件最大大小.
     */
    private static final String DEFAULT_LOG_MAX_SIZE = "100MB";

    /**
     * 默认日志文件最大索引.
     */
    private static final int DEFAULT_LOG_MAX_INDEX = 100;

    /**
     * 默认 JVM 参数.
     */
    private static final List<String> DEFAULT_JVM_OPTIONS = List.of(
            "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED");

    /**
     * Local template path.
     */
    private static final String LOCAL_TEMPLATE_PATH = "api/templates/api-local-runtime.yml";

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * Shell LOCAL 执行器.
     */
    private final ShellLocalPluginTaskExecutor delegate;

    /**
     * Template renderer.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * 构造函数.
     *
     * @param properties       agent 配置
     * @param delegate         Shell LOCAL 执行器
     * @param templateRenderer 模板渲染器
     */
    public ApiLocalPluginTaskExecutor(AgentProperties properties, ShellLocalPluginTaskExecutor delegate,
            TemplateSpecRenderer templateRenderer) {
        this.properties = properties;
        this.delegate = delegate;
        this.templateRenderer = templateRenderer;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return ApiLocalRunModeStateMapping.RUN_MODE;
    }

    @Override
    public void validateTaskRequest(TaskRequest request) {
        effectiveJob(request);
        delegate.validateTaskRequest(shellRequest(request));
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        writeJobFile(request);
        return delegate.submitTask(shellRequest(request));
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        return delegate.stopTask(request);
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        return delegate.killTask(request);
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        return delegate.finishTask(request);
    }

    @Override
    public void destroyTask(TaskRequest request) {
        delegate.destroyTask(request);
    }

    private TaskRequest shellRequest(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("TaskRequest不能为空");
        }
        ObjectNode shellPluginParam = shellPluginParam(request);
        ObjectNode shellTaskData = shellTaskData(request);
        TaskRequest shellRequest = new TaskRequest();
        shellRequest.setFlowInstanceId(request.getFlowInstanceId());
        shellRequest.setTaskInstanceId(request.getTaskInstanceId());
        shellRequest.setTaskName(request.getTaskName());
        shellRequest.setTaskState(request.getTaskState());
        shellRequest.setTaskData(shellTaskData);
        shellRequest.setPluginType(PLUGIN_TYPE);
        shellRequest.setRunMode(ApiLocalRunModeStateMapping.RUN_MODE);
        shellRequest.setPluginParam(shellPluginParam);
        shellRequest.setSubmitMode(request.getSubmitMode());
        shellRequest.setWorkerResult(copyWorkerResult(request.getWorkerResult()));
        return shellRequest;
    }

    private ObjectNode shellPluginParam(TaskRequest request) {
        JsonNode pluginParam = request.getPluginParam();
        List<String> command = apiLocalProcessSpec(request).getCommand();
        ObjectNode node = JacksonUtils.createObjectNode();
        node.put("command", command.get(0));
        ArrayNode args = node.putArray("args");
        command.subList(1, command.size()).forEach(args::add);
        copyObject(node, "env", object(pluginParam, "env"));
        putText(node, "pluginLogUri", text(pluginParam, "pluginLogUri"));
        return node;
    }

    private ObjectNode shellTaskData(TaskRequest request) {
        return JacksonUtils.createObjectNode();
    }

    private LocalProcessSpec apiLocalProcessSpec(TaskRequest request) {
        JsonNode pluginParam = request.getPluginParam();
        ApiLaunchMode launchMode = ApiLaunchMode.parse(text(pluginParam, "launchMode"));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("javaBin", firstText(text(pluginParam, "javaBin"), DEFAULT_JAVA_BIN));
        values.put("jvmOptions", TemplateYamlFragments.listItems(listOrDefault(pluginParam, "jvmOptions",
                DEFAULT_JVM_OPTIONS), 2));
        values.put("logHome", firstText(text(pluginParam, "logHome"), DEFAULT_LOG_HOME));
        values.put("logLevel", firstText(text(pluginParam, "logLevel"), DEFAULT_LOG_LEVEL));
        values.put("logMaxSize", firstText(text(pluginParam, "logMaxSize"), DEFAULT_LOG_MAX_SIZE));
        values.put("logMaxIndex", String.valueOf(intValue(pluginParam, "logMaxIndex", DEFAULT_LOG_MAX_INDEX)));
        values.put("logConfigFile", firstText(text(pluginParam, "logConfigFile"), DEFAULT_LOG_CONFIG_FILE));
        values.put("launchArgs", TemplateYamlFragments.listItems(launchArgs(pluginParam, launchMode), 2));
        values.put("jobFile", JOB_FILE_NAME);
        LocalProcessSpec spec = templateRenderer.renderYaml(LOCAL_TEMPLATE_PATH, values, LocalProcessSpec.class);
        if (spec.getCommand() == null || spec.getCommand().isEmpty()) {
            throw new IllegalArgumentException("API LOCAL command不能为空");
        }
        return spec;
    }

    private List<String> launchArgs(JsonNode pluginParam, ApiLaunchMode launchMode) {
        if (launchMode == ApiLaunchMode.CLASSPATH) {
            String classpath = required(text(pluginParam, "classpath"), "pluginParam.classpath不能为空");
            String mainClass = firstText(text(pluginParam, "mainClass"), DEFAULT_MAIN_CLASS);
            return List.of("-classpath", classpath, mainClass);
        }
        String apiJar = required(firstText(text(pluginParam, "apiJar"), DEFAULT_API_JAR), "pluginParam.apiJar不能为空");
        return List.of("-jar", apiJar);
    }

    private void writeJobFile(TaskRequest request) {
        Path jobFile = jobFile(request);
        try {
            Files.createDirectories(jobFile.getParent());
            Files.writeString(jobFile, JacksonUtils.compactJson(effectiveJob(request)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("准备API job文件失败: " + e.getMessage(), e);
        }
    }

    private void validateJob(JsonNode taskData) {
        if (taskData == null || !taskData.isObject() || taskData.isEmpty()) {
            throw new IllegalArgumentException("taskData必须为API job JSON对象");
        }
    }

    private JsonNode effectiveJob(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("TaskRequest不能为空");
        }
        JsonNode defaultTaskData = object(request.getPluginParam(), "defaultTaskData");
        ObjectNode result = defaultTaskData == null ? JacksonUtils.createObjectNode() : defaultTaskData.deepCopy();
        JsonNode taskData = request.getTaskData();
        if (taskData != null && taskData.isObject()) {
            deepMerge(result, taskData);
        }
        validateJob(result);
        return result;
    }

    private Path jobFile(TaskRequest request) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getStorage().getTaskRuntimeDir(), date, safePath(request.getFlowInstanceId()),
                safePath(request.getTaskInstanceId()), JOB_FILE_NAME);
    }

    private WorkerResult copyWorkerResult(WorkerResult workerResult) {
        if (workerResult == null) {
            return null;
        }
        return WorkerResult.builder()
                .workerId(workerResult.getWorkerId())
                .appId(workerResult.getAppId())
                .workDirPath(workerResult.getWorkDirPath())
                .message(workerResult.getMessage())
                .pluginLogUri(workerResult.getPluginLogUri())
                .build();
    }

    private void copyObject(ObjectNode target, String field, JsonNode value) {
        if (value != null && value.isObject()) {
            target.set(field, value);
        }
    }

    private void putText(ObjectNode target, String field, String value) {
        if (!isBlank(value)) {
            target.put(field, value);
        }
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

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        List<String> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private List<String> listOrDefault(JsonNode node, String field, List<String> defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) {
            return new ArrayList<>(defaultValue);
        }
        return list(node, field);
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? defaultValue : value.asInt();
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

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safePath(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    /**
     * API local launch mode.
     */
    private enum ApiLaunchMode {

        /**
         * Executable jar mode.
         */
        JAR,

        /**
         * Classpath mode.
         */
        CLASSPATH;

        private static ApiLaunchMode parse(String value) {
            if (value == null || value.trim().isEmpty()) {
                return JAR;
            }
            for (ApiLaunchMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("不支持的API启动模式: " + value);
        }
    }
}
