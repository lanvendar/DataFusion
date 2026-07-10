package com.datafusion.plugin.spark.sql.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spark SQL 作业配置测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class SparkSqlJobConfigTest {

    @Test
    void shouldLoadExample() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/plugins/spark/jobs/spark-sql-job-example.json")) {
            SparkSqlJobConfig config = new ObjectMapper().readValue(input, SparkSqlJobConfig.class);

            assertDoesNotThrow(config::validate);
            assertEquals("PAIMON", config.sqlTargetType);
            assertEquals(4, config.statements.size());
            assertFalse(config.useDatabase);
        }
    }

    @Test
    void shouldUseRuntimeDefaults() {
        SparkSqlJobConfig config = new SparkSqlJobConfig();

        assertTrue(config.useDatabase);
        assertTrue(config.enableSqlLogging);
        assertFalse(config.enableHiveSupport);
        assertFalse(config.allowSqlFailure);
        assertEquals(100, SparkSqlJobConfig.SELECT_LOG_ROW_LIMIT);
    }
}
