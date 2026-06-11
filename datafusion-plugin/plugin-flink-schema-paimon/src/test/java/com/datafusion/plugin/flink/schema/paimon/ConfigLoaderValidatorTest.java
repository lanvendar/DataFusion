package com.datafusion.plugin.flink.schema.paimon;

import com.datafusion.plugin.flink.schema.paimon.config.ConfigLoader;
import com.datafusion.plugin.flink.schema.paimon.config.ConfigValidator;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 作业配置加载与校验测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class ConfigLoaderValidatorTest {

    /**
     * 样例 job.json 应能加载并通过校验.
     *
     * @throws URISyntaxException 资源路径异常
     */
    @Test
    void shouldLoadAndValidateSampleJobConfig() throws URISyntaxException {
        Path path = Path.of(getClass().getClassLoader().getResource("sample-kafka-schema-paimon-job.json").toURI());

        FlinkSchemaPaimonJobConfig config = new ConfigLoader().load(path.toString());
        new ConfigValidator().validate(config);

        assertEquals("kafka_schema_paimon_demo", config.job.id);
        assertEquals("datafusion-plugin-flink-schema-paimon", config.source.groupId);
        assertFalse(config.sink.tables.isEmpty());
        assertNotNull(config.sink.write);
    }
}
