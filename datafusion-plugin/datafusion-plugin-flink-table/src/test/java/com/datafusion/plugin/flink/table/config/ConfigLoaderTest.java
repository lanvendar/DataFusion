package com.datafusion.plugin.flink.table.config;

import com.datafusion.plugin.flink.table.core.FlinkTableException;
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
     * 新配置字段 flinkConfig 可以正常加载.
     */
    @Test
    void shouldLoadFlinkConfig() {
        ConfigLoader loader = new ConfigLoader();
        String content = "{\"job\":{\"id\":\"job-a\"},\"flinkConfig\":{\"parallelism.default\":\"2\"}}";

        FlinkTableJobConfig config = loader.loadContent(content);

        Assertions.assertEquals("2", config.flinkConfig.get("parallelism.default"));
    }

    /**
     * 环境变量占位符缺失时启动期失败.
     */
    @Test
    void shouldFailWhenEnvironmentVariableMissing() {
        ConfigLoader loader = new ConfigLoader();
        String content = "{\"job\":{\"id\":\"job-a\"},\"sink\":{\"options\":{\"warehouse\":\"${env:DATAFUSION_MISSING_ENV}\"}}}";

        Assertions.assertThrows(FlinkTableException.class, () -> loader.loadContent(content));
    }
}
