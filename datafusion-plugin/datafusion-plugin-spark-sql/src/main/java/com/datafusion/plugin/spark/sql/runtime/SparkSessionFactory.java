package com.datafusion.plugin.spark.sql.runtime;

import com.datafusion.plugin.spark.sql.config.SparkSqlJobConfig;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * SparkSession 工厂.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SparkSessionFactory {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkSessionFactory.class);

    private SparkSessionFactory() {
    }

    /**
     * 创建 SparkSession.
     *
     * @param config 作业配置
     * @return SparkSession
     */
    public static SparkSession create(SparkSqlJobConfig config) {
        String appName = config.job.name == null || config.job.name.isBlank() ? config.job.id : config.job.name;
        SparkSession.Builder builder = SparkSession.builder().appName(appName);
        config.sparkConf.forEach(builder::config);
        for (Map.Entry<String, String> entry : config.paimonConf.entrySet()) {
            if (config.sparkConf.containsKey(entry.getKey())) {
                LOGGER.warn("参数[{}]失效，使用paimonConf", entry.getKey());
            }
            builder.config(entry.getKey(), entry.getValue());
        }
        if (config.enableHiveSupport) {
            builder.enableHiveSupport();
        }
        SparkSession session = builder.getOrCreate();
        config.hadoopConf.forEach(session.sparkContext().hadoopConfiguration()::set);
        return session;
    }
}
