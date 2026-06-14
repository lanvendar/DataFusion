package com.datafusion.plugin.kafka.json.config;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * ConfigLoader 单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class ConfigLoaderTest {

    /**
     * 环境变量占位符缺失时启动期失败.
     */
    @Test
    void shouldFailWhenEnvironmentVariableMissing() {
        ConfigLoader loader = new ConfigLoader();
        String content = "{\"job\":{\"id\":\"job-a\"},\"sink\":{\"options\":{\"warehouse\":\"${env:DATAFUSION_MISSING_ENV}\"}}}";

        Assertions.assertThrows(KafkaJsonPaimonException.class, () -> loader.loadContent(content));
    }
}
