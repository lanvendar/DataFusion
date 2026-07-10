package com.datafusion.plugin.spark.sql;

import com.datafusion.plugin.spark.sql.config.SparkSqlJobConfig;
import com.datafusion.plugin.spark.sql.runtime.SparkSessionFactory;
import com.datafusion.plugin.spark.sql.runtime.SparkSqlRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spark SQL 插件入口.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class SparkSqlApplication {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkSqlApplication.class);

    /**
     * Job 文件参数.
     */
    private static final String JOB_FILE_OPTION = "--job-file";

    /**
     * 启动 Spark SQL 作业.
     *
     * @param args 启动参数
     * @throws Exception 配置读取失败
     */
    public static void main(String[] args) throws Exception {
        SparkSqlJobConfig config = loadConfig(args);
        config.validate();
        LOGGER.info("Spark SQL任务启动, jobId={}", config.job.id);

        SparkSession session = null;
        try {
            session = SparkSessionFactory.create(config);
            SparkSqlRunner.run(session, config);
        } finally {
            if (session != null) {
                session.stop();
            }
        }
    }

    /**
     * 加载作业配置.
     *
     * @param args 启动参数
     * @return 作业配置
     * @throws Exception 配置读取失败
     */
    static SparkSqlJobConfig loadConfig(String[] args) throws Exception {
        if (args == null || args.length != 2 || !JOB_FILE_OPTION.equals(args[0]) || args[1].isBlank()) {
            throw new IllegalArgumentException("启动参数必须为--job-file <path>");
        }
        Path jobFile = Path.of(args[1]);
        if (!Files.isRegularFile(jobFile)) {
            throw new IllegalArgumentException("Job文件不存在: " + jobFile);
        }
        return new ObjectMapper().readValue(jobFile.toFile(), SparkSqlJobConfig.class);
    }
}
