package com.datafusion.agent.runtime.worker.plugin.spark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spark 插件配置模板测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/13
 * @since 1.0.0
 */
class SparkPluginConfigTemplateTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldUseStandardPluginConfigStructure() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "plugins/spark/templates/spark-k8s-operator-plugin-config.json")) {
            assertNotNull(inputStream);
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            JsonNode pluginParam = root.path("pluginParam");
            final JsonNode defaultTaskData = pluginParam.path("defaultTaskData");

            assertEquals("SPARK", root.path("pluginType").asText());
            assertEquals("K8S_OPERATOR", root.path("runMode").asText());
            assertTrue(root.path("isTemplate").asBoolean());
            assertTrue(pluginParam.isObject());
            assertFalse(pluginParam.has("runMode"));
            assertEquals("df-spark", pluginParam.path("kubernetes").path("namePrefix").asText());
            assertFalse(pluginParam.path("kubernetes").has("applicationNamePrefix"));
            assertFalse(pluginParam.path("kubernetes").has("configMapNamePrefix"));
            assertEquals("PAIMON", defaultTaskData.path("sqlTargetType").asText());
            assertTrue(defaultTaskData.path("statements").isArray());
            assertTrue(defaultTaskData.path("paimonConf").isObject());
            assertFalse(defaultTaskData.has("applicationType"));
            assertFalse(defaultTaskData.has("runtime"));
        }
    }
}
