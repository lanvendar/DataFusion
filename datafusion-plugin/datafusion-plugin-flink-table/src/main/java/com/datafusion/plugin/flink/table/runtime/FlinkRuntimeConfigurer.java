package com.datafusion.plugin.flink.table.runtime;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flink 配置器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class FlinkRuntimeConfigurer {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FlinkRuntimeConfigurer.class);

    /**
     * 应用 Flink 配置.
     *
     * @param env Flink 执行环境
     * @param flinkConfig Flink 配置
     */
    public void configure(StreamExecutionEnvironment env, Map<String, String> flinkConfig) {
        Map<String, String> actual = flinkConfig == null ? new LinkedHashMap<>() : flinkConfig;
        if (actual.isEmpty()) {
            LOGGER.info("No Flink config configured");
            return;
        }
        env.configure(Configuration.fromMap(actual));
        LOGGER.info("Flink config applied, keys={}", actual.keySet());
    }
}
