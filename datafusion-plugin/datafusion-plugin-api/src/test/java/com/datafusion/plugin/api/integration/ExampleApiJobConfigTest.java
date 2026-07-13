package com.datafusion.plugin.api.integration;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ConfigValidator;
import com.datafusion.plugin.api.util.JsonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * API 作业示例配置测试.
 *
 * @author DataFusion
 * @version 1.0.0, 2026/7/13
 * @since 1.0.0
 */
class ExampleApiJobConfigTest {

    /**
     * 示例配置资源路径.
     */
    private static final String CONFIG_FILE = "plugins/api/jobs/example-api-job.json";

    @Test
    void shouldProvideValidGenericPaimonJobConfig() throws Exception {
        String json = loadConfigText();
        ApiExtractJobConfig config = JsonUtils.read(json, ApiExtractJobConfig.class);

        Assertions.assertEquals("example_api_to_paimon", config.job.id);
        Assertions.assertEquals("PAIMON", config.sink.type);
        Assertions.assertEquals("S3", config.sink.connectType);
        Assertions.assertEquals(4, config.steps.get(0).response.fields.size());
        Assertions.assertEquals(4, config.sink.columns.size());
        Assertions.assertTrue(config.sink.table.primaryKeys.contains("day_pt"));
        Assertions.assertTrue(config.sink.table.partitionKeys.contains("day_pt"));
        Assertions.assertFalse(json.contains("sz-s3.indusmind.me"));
        Assertions.assertDoesNotThrow(() -> new ConfigValidator().validate(config));
    }

    private String loadConfigText() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            Assertions.assertNotNull(inputStream, "配置文件不存在: " + CONFIG_FILE);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
