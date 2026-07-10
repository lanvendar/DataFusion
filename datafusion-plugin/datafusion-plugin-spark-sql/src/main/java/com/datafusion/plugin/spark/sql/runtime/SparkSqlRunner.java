package com.datafusion.plugin.spark.sql.runtime;

import com.datafusion.plugin.spark.sql.config.SparkSqlJobConfig;
import com.datafusion.plugin.spark.sql.config.SparkSqlJobConfig.SqlStatement;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spark SQL 执行器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SparkSqlRunner {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkSqlRunner.class);

    private SparkSqlRunner() {
    }

    /**
     * 执行 Spark SQL 作业.
     *
     * @param session SparkSession
     * @param config  作业配置
     * @throws Exception SQL 执行失败
     */
    public static void run(SparkSession session, SparkSqlJobConfig config) throws Exception {
        if (config.useDatabase) {
            session.catalog().setCurrentCatalog(config.catalogName);
            session.catalog().setCurrentDatabase(config.databaseName);
        }

        int succeeded = 0;
        int failed = 0;
        int total = config.statements.size();
        try {
            for (int i = 0; i < total; i++) {
                SqlStatement statement = config.statements.get(i);
                int sequence = i + 1;
                if (config.enableSqlLogging) {
                    LOGGER.info("SQL[{}/{}]: {}", sequence, total, statement.sql);
                } else if (statement.comment != null && !statement.comment.isBlank()) {
                    LOGGER.info("SQL[{}/{}]: {}", sequence, total, statement.comment);
                }
                try {
                    Dataset<Row> result = session.sql(statement.sql);
                    if (result.columns().length > 0) {
                        result.show(SparkSqlJobConfig.SELECT_LOG_ROW_LIMIT, false);
                    }
                    succeeded++;
                } catch (Exception e) {
                    failed++;
                    LOGGER.error("SQL[{}/{}]失败: {}", sequence, total, e.getMessage(), e);
                    if (!config.allowSqlFailure) {
                        throw e;
                    }
                }
            }
        } finally {
            LOGGER.info("SQL执行完成, total={}, succeeded={}, failed={}", total, succeeded, failed);
        }
    }
}
