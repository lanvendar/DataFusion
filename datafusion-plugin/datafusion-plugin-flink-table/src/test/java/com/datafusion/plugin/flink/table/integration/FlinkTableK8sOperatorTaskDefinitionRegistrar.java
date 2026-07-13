package com.datafusion.plugin.flink.table.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * 将内置 Kafka JSON Flink 任务 JSON 注册为调度系统的 Flink K8S_OPERATOR 任务定义。
 *
 * <p>直接运行 {@link #main(String[])} 时，默认处理
 * {@code plugins/flink/jobs/ods-spider-sites-job.json}。
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FlinkTableK8sOperatorTaskDefinitionRegistrar {

    /**
     * 日志对象。
     */
    private static final Logger LOGGER = Logger.getLogger(FlinkTableK8sOperatorTaskDefinitionRegistrar.class.getName());

    /**
     * JSON 处理对象。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Manager 接口成功编码。
     */
    private static final String SUCCESS_CODE = "00000";

    /**
     * Kafka JSON Flink 任务 JSON 资源根目录。
     */
    private static final String JOB_RESOURCE_ROOT = "plugins/flink/jobs";

    /**
     * Flink K8S_OPERATOR 任务编码前缀。
     */
    private static final String TASK_CODE_PREFIX = "FLINK_K8S_OPERATOR_";

    /**
     * Flink 插件业务系统。
     */
    private static final String BIZ_SYSTEM = "DATAFUSION_PLUGIN_FLINK";

    /**
     * 只生成并打印注册 JSON 的动作。
     */
    private static final String ACTION_GENERATE = "generate";

    /**
     * 只调用注册接口的动作。
     */
    private static final String ACTION_REGISTER = "register";

    /**
     * 先生成并打印注册 JSON，再调用注册接口的动作。
     */
    private static final String ACTION_GENERATE_AND_REGISTER = "generate-and-register";

    /**
     * 命令行使用说明。
     */
    private static final String USAGE = "用法：KafkaJsonK8sOperatorTaskDefinitionRegistrar "
            + "[generate|register|generate-and-register] <job-file-name-or-resource-path>";

    /**
     * 直接运行 main 时需要修改：Manager 服务地址。
     */
    private static final String DEFAULT_API_BASE_URL = "https://sz-dev-datafusion-web.indusmind.me";

    /**
     * 直接运行 main 时按需修改：任务定义注册接口路径。
     */
    private static final String DEFAULT_REGISTER_PATH = "/api/scheduler/task-definition/register";

    /**
     * 直接运行 main 时按需修改：Authorization 请求头。
     */
    private static final String DEFAULT_AUTHORIZATION = "";

    /**
     * 直接运行 main 时按需修改：Cookie 请求头。
     */
    private static final String DEFAULT_COOKIE = "";

    /**
     * 直接运行 main 时按需修改：任务类型。
     */
    private static final String DEFAULT_TASK_TYPE = "FLINK";

    /**
     * 直接运行 main 时按需修改：任务类型 ID。
     */
    private static final String DEFAULT_TASK_TYPE_ID = DEFAULT_TASK_TYPE;

    /**
     * Manager 服务地址。
     */
    private final String apiBaseUrl = setting("datafusion.manager.api-base-url",
            "DATAFUSION_MANAGER_API_BASE_URL", DEFAULT_API_BASE_URL);

    /**
     * 任务定义注册接口路径。
     */
    private final String registerPath = setting("datafusion.manager.task-register-path",
            "DATAFUSION_MANAGER_TASK_REGISTER_PATH", DEFAULT_REGISTER_PATH);

    /**
     * Authorization 请求头。
     */
    private final String authorization = setting("datafusion.manager.authorization",
            "DATAFUSION_MANAGER_AUTHORIZATION", DEFAULT_AUTHORIZATION);

    /**
     * Cookie 请求头。
     */
    private final String cookie = setting("datafusion.manager.cookie", "DATAFUSION_MANAGER_COOKIE", DEFAULT_COOKIE);

    /**
     * 任务类型。
     */
    private final String taskType = setting("datafusion.flink.task-type",
            "DATAFUSION_FLINK_TASK_TYPE", DEFAULT_TASK_TYPE);

    /**
     * 任务类型 ID。
     */
    private final String taskTypeId = setting("datafusion.flink.task-type-id",
            "DATAFUSION_FLINK_TASK_TYPE_ID", DEFAULT_TASK_TYPE_ID);

    /**
     * 解析后的 Kafka JSON Flink 任务 JSON classpath 资源路径。
     */
    private final String jobResource;

    /**
     * 任务 JSON。
     */
    private final JsonNode jobJson;

    /**
     * 创建注册工具。
     *
     * @param jobResourceInput 任务 JSON 文件名或 classpath 资源路径
     * @throws IOException 扫描任务 JSON 资源失败
     */
    private FlinkTableK8sOperatorTaskDefinitionRegistrar(String jobResourceInput) throws IOException {
        this.jobResource = resolveJobResource(jobResourceInput);
        this.jobJson = readJobJson();
    }

    /**
     * 直接运行 main 时需要修改：默认执行动作。
     */
    private static final String DEFAULT_ACTION = ACTION_GENERATE_AND_REGISTER;

    /**
     * 默认注册的 Kafka JSON Flink 任务 JSON。
     */
    private static final String DEFAULT_JOB_RESOURCE = "ods-spider-sites-job.json";

    /**
     * 生成或注册 Kafka JSON Flink K8S_OPERATOR 任务定义。
     *
     * @param args 第一个参数可以是动作，后一个参数是任务 JSON 文件名或 classpath 资源路径
     * @throws Exception 生成或注册失败
     */
    public static void main(String[] args) throws Exception {
        String action = action(args);
        String jobResourceInput = jobResourceInput(args);
        FlinkTableK8sOperatorTaskDefinitionRegistrar registrar =
                new FlinkTableK8sOperatorTaskDefinitionRegistrar(jobResourceInput);
        if (ACTION_GENERATE.equals(action) || ACTION_GENERATE_AND_REGISTER.equals(action)) {
            registrar.printRegisterPayload();
        }
        if (ACTION_REGISTER.equals(action) || ACTION_GENERATE_AND_REGISTER.equals(action)) {
            registrar.register();
        }
    }

    /**
     * 打印注册请求 JSON。
     *
     * @throws Exception 读取任务 JSON 失败
     */
    private void printRegisterPayload() throws Exception {
        JsonNode payload = buildRegisterPayload();
        validatePayload(payload);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        writer.print(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        writer.println();
    }

    /**
     * 注册任务定义。
     *
     * @throws Exception 注册失败
     */
    private void register() throws Exception {
        require(!apiBaseUrl.isBlank(), "必须显式配置 Manager 服务地址。");
        JsonNode payload = buildRegisterPayload();
        validatePayload(payload);

        HttpResponse<String> response = post(apiUrl(), payload);
        require(response.statusCode() >= 200 && response.statusCode() < 300,
                "HTTP " + response.statusCode() + ": " + response.body());

        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        require(SUCCESS_CODE.equals(body.path("code").asText()), "注册失败：" + response.body());
        require(taskCode().equals(body.path("data").path("taskCode").asText()),
                "接口返回的任务编码不符合预期：" + response.body());
        require(body.path("data").path("syncFlag").asBoolean(), "任务定义同步标记为 false：" + response.body());
        require(body.path("data").path("taskId").asText(null) != null, "接口返回缺少任务 ID：" + response.body());

        LOGGER.info(() -> "Kafka JSON Flink K8S_OPERATOR 任务定义注册成功，taskCode=" + taskCode()
                + ", taskId=" + body.path("data").path("taskId").asText());
    }

    /**
     * 校验注册请求 JSON。
     *
     * @param payload 注册请求 JSON
     */
    private void validatePayload(JsonNode payload) {
        require(taskCode().equals(payload.path("taskCode").asText()), "注册请求中的任务编码不符合预期。");
        require(taskType.equals(payload.path("taskType").asText()), "注册请求中的任务类型不符合预期。");
        require(taskTypeId.equals(payload.path("taskTypeId").asText()), "注册请求中的任务类型 ID 不符合预期。");
        require(!payload.path("definition").path("job").path("id").asText().isBlank(), "Flink job.id 不能为空。");
        require(!payload.path("definition").path("source").isMissingNode(), "Flink source 配置不能为空。");
        require(!payload.path("definition").path("sink").isMissingNode(), "Flink sink 配置不能为空。");
    }

    /**
     * 构造注册请求 JSON。
     *
     * @return 注册请求 JSON
     */
    private ObjectNode buildRegisterPayload() {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("taskName", taskName());
        payload.put("taskCode", taskCode());
        payload.put("description", "注册内置 Kafka JSON Flink K8S_OPERATOR 任务定义：" + taskName());
        payload.put("taskTypeId", taskTypeId);
        payload.put("taskType", taskType);
        payload.set("taskParam", OBJECT_MAPPER.createObjectNode());
        payload.set("definition", taskDefinition());
        ObjectNode sourceRoute = payload.putObject("sourceRoute");
        sourceRoute.put("bizSystem", BIZ_SYSTEM);
        sourceRoute.put("bizKey", jobId());
        sourceRoute.put("bizVersion", jobJson.path("job").path("version").asText("1.0.0"));
        return payload;
    }

    /**
     * 构造任务定义。
     *
     * @return 任务定义
     */
    private ObjectNode taskDefinition() {
        return (ObjectNode) jobJson.deepCopy();
    }

    /**
     * 读取 Kafka JSON Flink 任务 JSON。
     *
     * @return 任务 JSON
     * @throws IOException 读取任务 JSON 失败
     */
    private JsonNode readJobJson() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(jobResource)) {
            require(inputStream != null, "Kafka JSON Flink 任务资源不存在：" + jobResource);
            return OBJECT_MAPPER.readTree(inputStream);
        }
    }

    /**
     * 构造任务编码。
     *
     * @return 任务编码
     */
    private String taskCode() {
        return TASK_CODE_PREFIX + jobId().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    /**
     * 构造任务名称。
     *
     * @return 任务名称
     */
    private String taskName() {
        String configuredName = jobJson.path("job").path("name").asText("");
        if (!configuredName.isBlank()) {
            return configuredName;
        }
        return jobId();
    }

    /**
     * 获取 Flink job ID。
     *
     * @return Flink job ID
     */
    private String jobId() {
        String jobId = jobJson.path("job").path("id").asText("");
        if (!jobId.isBlank()) {
            return jobId;
        }
        return jobKey(jobResource);
    }

    /**
     * 构造注册接口地址。
     *
     * @return 注册接口地址
     */
    private URI apiUrl() {
        String baseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        return URI.create(baseUrl + registerPath);
    }

    /**
     * 发送注册请求。
     *
     * @param uri 注册接口地址
     * @param payload 注册请求 JSON
     * @return HTTP 响应
     * @throws Exception HTTP 请求失败
     */
    private HttpResponse<String> post(URI uri, JsonNode payload) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload),
                        StandardCharsets.UTF_8));
        if (!authorization.isEmpty()) {
            requestBuilder.header("Authorization", authorization);
        }
        if (!cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        }
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 根据输入解析任务 JSON 资源路径。
     *
     * @param jobResourceInput 任务 JSON 文件名或资源路径
     * @return classpath 任务 JSON 资源路径
     * @throws IOException 扫描任务 JSON 资源失败
     */
    private String resolveJobResource(String jobResourceInput) throws IOException {
        String normalizedInput = normalizeResourcePath(jobResourceInput);
        if (normalizedInput.startsWith(JOB_RESOURCE_ROOT + "/")) {
            require(resourceExists(normalizedInput), "Kafka JSON Flink 任务资源不存在：" + normalizedInput);
            return normalizedInput;
        }
        if (normalizedInput.contains("/")) {
            String resourcePath = JOB_RESOURCE_ROOT + "/" + normalizedInput;
            require(resourceExists(resourcePath), "Kafka JSON Flink 任务资源不存在：" + resourcePath);
            return resourcePath;
        }

        String defaultResourcePath = JOB_RESOURCE_ROOT + "/" + normalizedInput;
        if (resourceExists(defaultResourcePath)) {
            return defaultResourcePath;
        }
        if (resourceExists(normalizedInput)) {
            return normalizedInput;
        }

        List<String> matchedResources = findJobResources(normalizedInput);
        require(!matchedResources.isEmpty(), "classpath 的 " + JOB_RESOURCE_ROOT
                + " 资源目录下不存在 Kafka JSON Flink 任务文件：" + normalizedInput);
        require(matchedResources.size() == 1, "匹配到多个 Kafka JSON Flink 任务文件 "
                + normalizedInput + "：" + matchedResources);
        return matchedResources.get(0);
    }

    /**
     * 在文件系统 classpath 中查找匹配的任务 JSON 资源。
     *
     * @param fileName 任务 JSON 文件名
     * @return 匹配到的资源路径
     * @throws IOException 扫描 classpath 失败
     */
    private List<String> findJobResources(String fileName) throws IOException {
        List<String> matchedResources = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> jobRoots = classLoader.getResources(JOB_RESOURCE_ROOT);
        while (jobRoots.hasMoreElements()) {
            URL jobRoot = jobRoots.nextElement();
            if (!Objects.equals("file", jobRoot.getProtocol())) {
                continue;
            }
            Path rootPath = Path.of(URI.create(jobRoot.toString()));
            try (var stream = Files.walk(rootPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> fileName.equals(path.getFileName().toString()))
                        .map(path -> JOB_RESOURCE_ROOT + "/" + rootPath.relativize(path).toString().replace('\\', '/'))
                        .forEach(matchedResources::add);
            }
        }
        return matchedResources;
    }

    /**
     * 判断 classpath 资源是否存在。
     *
     * @param resourcePath classpath 资源路径
     * @return 存在时返回 true
     */
    private static boolean resourceExists(String resourcePath) {
        return Thread.currentThread().getContextClassLoader().getResource(resourcePath) != null;
    }

    /**
     * 根据资源路径推导任务键。
     *
     * @param resourcePath classpath 资源路径
     * @return 任务键
     */
    private static String jobKey(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionIndex);
    }

    /**
     * 获取执行动作。
     *
     * @param args 命令行参数
     * @return 执行动作
     */
    private static String action(String[] args) {
        if (firstArgIsAction(args)) {
            return normalizeAction(args[0]);
        }
        String configuredAction = setting("datafusion.flink.action", "DATAFUSION_FLINK_ACTION", DEFAULT_ACTION);
        require(isAction(configuredAction), "执行动作不合法：" + configuredAction + "，" + USAGE);
        return normalizeAction(configuredAction);
    }

    /**
     * 获取任务 JSON 资源输入。
     *
     * @param args 命令行参数
     * @return 任务 JSON 资源输入
     */
    private static String jobResourceInput(String[] args) {
        int jobResourceIndex = firstArgIsAction(args) ? 1 : 0;
        if (args != null && args.length > jobResourceIndex && !args[jobResourceIndex].isBlank()) {
            return args[jobResourceIndex];
        }
        String configuredResource = setting("datafusion.flink.job-resource",
                "DATAFUSION_FLINK_JOB_RESOURCE", DEFAULT_JOB_RESOURCE);
        require(!configuredResource.isBlank(), USAGE);
        return configuredResource;
    }

    /**
     * 判断第一个参数是否为执行动作。
     *
     * @param args 命令行参数
     * @return 第一个参数为执行动作时返回 true
     */
    private static boolean firstArgIsAction(String[] args) {
        return args != null && args.length > 0 && isAction(args[0]);
    }

    /**
     * 判断参数值是否为执行动作。
     *
     * @param value 参数值
     * @return 参数值为执行动作时返回 true
     */
    private static boolean isAction(String value) {
        String action = normalizeAction(value);
        return ACTION_GENERATE.equals(action)
                || ACTION_REGISTER.equals(action)
                || ACTION_GENERATE_AND_REGISTER.equals(action);
    }

    /**
     * 规范化执行动作。
     *
     * @param value 参数值
     * @return 规范化后的执行动作
     */
    private static String normalizeAction(String value) {
        String action = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        while (action.startsWith("-")) {
            action = action.substring(1);
        }
        return action;
    }

    /**
     * 按系统属性、环境变量、默认值的优先级读取配置。
     *
     * @param propertyName 系统属性名
     * @param envName 环境变量名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String setting(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue.trim();
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        return defaultValue;
    }

    /**
     * 规范化资源路径。
     *
     * @param resourcePath 资源路径
     * @return 规范化后的资源路径
     */
    private static String normalizeResourcePath(String resourcePath) {
        String normalizedPath = resourcePath.trim().replace('\\', '/');
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        while (normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }
        return normalizedPath;
    }

    /**
     * 要求表达式为 true。
     *
     * @param expression 表达式
     * @param message 错误消息
     */
    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }
}
