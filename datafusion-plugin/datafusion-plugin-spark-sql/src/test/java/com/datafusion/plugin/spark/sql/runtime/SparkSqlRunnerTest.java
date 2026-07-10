package com.datafusion.plugin.spark.sql.runtime;

import com.datafusion.plugin.spark.sql.config.SparkSqlJobConfig;
import com.datafusion.plugin.spark.sql.config.SparkSqlJobConfig.SqlStatement;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spark SQL 执行器测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class SparkSqlRunnerTest {

    @Test
    void shouldStopWhenSqlFails() {
        SparkSession session = mock(SparkSession.class);
        SparkSqlJobConfig config = config("bad sql");
        when(session.sql("bad sql")).thenThrow(new IllegalArgumentException("bad sql"));

        assertThrows(IllegalArgumentException.class, () -> SparkSqlRunner.run(session, config));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldContinueWhenSqlFailureIsAllowed() {
        SparkSession session = mock(SparkSession.class);
        SparkSqlJobConfig config = config("bad sql", "select 1");
        config.allowSqlFailure = true;
        Dataset<Row> result = mock(Dataset.class);
        when(session.sql("bad sql")).thenThrow(new IllegalArgumentException("bad sql"));
        when(session.sql("select 1")).thenReturn(result);
        when(result.columns()).thenReturn(new String[]{"value"});

        assertDoesNotThrow(() -> SparkSqlRunner.run(session, config));
        verify(result).show(100, false);
    }

    private SparkSqlJobConfig config(String... sqlList) {
        SparkSqlJobConfig config = new SparkSqlJobConfig();
        config.useDatabase = false;
        for (String sql : sqlList) {
            SqlStatement statement = new SqlStatement();
            statement.sql = sql;
            config.statements.add(statement);
        }
        return config;
    }
}
