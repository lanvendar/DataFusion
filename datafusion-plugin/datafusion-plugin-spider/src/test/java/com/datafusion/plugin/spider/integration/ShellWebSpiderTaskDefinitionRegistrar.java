package com.datafusion.plugin.spider.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register sh-web-spider site commands as scheduler SPIDER LOCAL task definitions.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/23
 * @since 1.0.0
 */
class ShellWebSpiderTaskDefinitionRegistrar {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Manager success code.
     */
    private static final String SUCCESS_CODE = "00000";

    /**
     * Spider task type.
     */
    private static final String TASK_TYPE = "SPIDER";

    /**
     * Spider 任务定义版本.
     */
    private static final String BIZ_VERSION = "1.0.0";

    /**
     * Spider project runtime directory on agent.
     */
    private static final String SPIDER_HOME = "/opt/sh-web-spider";

    /**
     * Business date range placeholder rendered by scheduler.
     */
    private static final String BIZ_DATE_RANGE_PLACEHOLDER = "#day(_biz_date_, -1D, yyyyMMdd)"
            + "-#day(_biz_date_, yyyyMMdd)";

    /**
     * Business date range placeholder with single quoted arguments.
     */
    private static final String QUOTED_BIZ_DATE_RANGE_PLACEHOLDER = "#day(_biz_date_, '-1D', 'yyyyMMdd')"
            + "-#day(_biz_date_, 'yyyyMMdd')";

    /**
     * Task specs, one site per task.
     */
    private static final List<SpiderTaskSpec> TASK_SPECS = List.of(
            new SpiderTaskSpec("爬虫-卓创-sci99", "sci99", BIZ_DATE_RANGE_PLACEHOLDER),
            new SpiderTaskSpec("爬虫-隆众-oilchem", "oilchem", BIZ_DATE_RANGE_PLACEHOLDER),
            new SpiderTaskSpec("爬虫-金联创-jlc", "jlc", BIZ_DATE_RANGE_PLACEHOLDER),
            new SpiderTaskSpec("爬虫-百川-baiinfo", "baiinfo", BIZ_DATE_RANGE_PLACEHOLDER),
            new SpiderTaskSpec("爬虫-普氏-platts", "platts", BIZ_DATE_RANGE_PLACEHOLDER),
            new SpiderTaskSpec("爬虫-煤炭资源网-cci", "cci", QUOTED_BIZ_DATE_RANGE_PLACEHOLDER),
            new SpiderTaskSpec("爬虫-中国货币网-bkccpr", "bkccpr", null),
            new SpiderTaskSpec("爬虫-国际化学品工业协会-icis", "icis", null));

    /**
     * Manager API base URL.
     */
    private final String apiBaseUrl = setting("datafusion.manager.api-base-url",
            "DATAFUSION_MANAGER_API_BASE_URL", "");

    /**
     * Task definition register API path.
     */
    private final String registerPath = setting("datafusion.manager.task-register-path",
            "DATAFUSION_MANAGER_TASK_REGISTER_PATH", "/api/scheduler/task-definition/register");

    /**
     * Optional authorization header value.
     */
    private final String authorization = setting("datafusion.manager.authorization",
            "DATAFUSION_MANAGER_AUTHORIZATION", "");

    /**
     * Optional cookie header value.
     */
    private final String cookie = setting("datafusion.manager.cookie", "DATAFUSION_MANAGER_COOKIE", "");

    /**
     * Task type ID.
     */
    private final String taskTypeId = setting("datafusion.spider.task-type-id",
            "DATAFUSION_SPIDER_TASK_TYPE_ID", TASK_TYPE);

    /**
     * Register one SPIDER LOCAL task per sh-web-spider site.
     *
     * @throws Exception register failed
     */
    @Test
    @Disabled("Manual integration utility: requires explicit manager API base URL and writes task definitions.")
    void registerShellWebSpiderTaskDefinitions() throws Exception {
        assertFalse(apiBaseUrl.isBlank(), "Manager API base URL must be configured explicitly.");
        for (SpiderTaskSpec spec : TASK_SPECS) {
            JsonNode payload = buildRegisterPayload(spec);
            validatePayload(payload, spec);

            HttpResponse<String> response = post(apiUrl(), payload);
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                    () -> "HTTP " + response.statusCode() + ": " + response.body());

            JsonNode body = OBJECT_MAPPER.readTree(response.body());
            assertEquals(SUCCESS_CODE, body.path("code").asText(), () -> "Register failed: " + response.body());
            assertEquals(taskCode(spec), body.path("data").path("taskCode").asText());
            assertTrue(body.path("data").path("syncFlag").asBoolean());
            assertNotNull(body.path("data").path("taskId").asText(null));
        }
    }

    private void validatePayload(JsonNode payload, SpiderTaskSpec spec) {
        assertEquals(taskCode(spec), payload.path("taskCode").asText());
        assertEquals(TASK_TYPE, payload.path("taskType").asText());
        assertEquals(taskTypeId, payload.path("taskTypeId").asText());
        assertEquals(spec.title(), payload.path("taskName").asText());
        assertEquals("day_1", payload.path("taskParam").path("vars").path("_biz_align_").path("value").asText());
        assertEquals(command(spec), payload.path("definition").path("args").get(0).asText());
        assertFalse(payload.path("definition").path("env").isMissingNode());
    }

    private ObjectNode buildRegisterPayload(SpiderTaskSpec spec) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("taskName", spec.title());
        payload.put("taskCode", taskCode(spec));
        payload.put("description", "Run " + spec.title() + " by SPIDER LOCAL.");
        payload.put("taskTypeId", taskTypeId);
        payload.put("taskType", TASK_TYPE);
        payload.set("taskParam", taskParam());
        payload.set("definition", taskDefinition(spec));
        ObjectNode sourceRoute = payload.putObject("sourceRoute");
        sourceRoute.put("bizSystem", "SH_WEB_SPIDER");
        sourceRoute.put("bizKey", spec.site());
        sourceRoute.put("bizVersion", BIZ_VERSION);
        return payload;
    }

    private ObjectNode taskParam() {
        ObjectNode param = OBJECT_MAPPER.createObjectNode();
        ObjectNode vars = param.putObject("vars");
        ObjectNode bizAlign = vars.putObject("_biz_align_");
        bizAlign.put("name", "_biz_align_");
        bizAlign.put("type", "in");
        bizAlign.put("value", "day_1");
        return param;
    }

    private ObjectNode taskDefinition(SpiderTaskSpec spec) {
        ObjectNode definition = OBJECT_MAPPER.createObjectNode();
        ArrayNode args = definition.putArray("args");
        args.add(command(spec));
        definition.set("env", OBJECT_MAPPER.createObjectNode());
        definition.put("pluginLogUri", "");
        return definition;
    }

    private String command(SpiderTaskSpec spec) {
        StringBuilder command = new StringBuilder();
        command.append("cd ").append(SPIDER_HOME).append(" && ./run-spider.sh --site ").append(spec.site());
        if (spec.dateRange() != null && !spec.dateRange().isBlank()) {
            command.append(" --date-range ").append(spec.dateRange());
        }
        return command.toString();
    }

    private String taskCode(SpiderTaskSpec spec) {
        return "SPIDER_WEB_" + spec.site().toUpperCase();
    }

    private URI apiUrl() {
        String baseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        return URI.create(baseUrl + registerPath);
    }

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
     * Spider task spec.
     *
     * @param title     task title
     * @param site      site name
     * @param dateRange optional date range
     */
    private record SpiderTaskSpec(String title, String site, String dateRange) {
    }
}
