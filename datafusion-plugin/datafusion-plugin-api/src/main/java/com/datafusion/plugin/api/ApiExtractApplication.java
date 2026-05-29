package com.datafusion.plugin.api;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ApiExtractResult;
import com.datafusion.plugin.api.core.ApiExtractRunner;
import com.datafusion.plugin.api.core.DefaultApiExtractRunner;
import com.datafusion.plugin.api.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * API 抽取插件命令行入口.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ApiExtractApplication {
    
    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExtractApplication.class);
    
    /**
     * 启动 API 抽取任务.
     *
     * @param args 命令行参数
     * @throws Exception 启动失败时抛出
     */
    public static void main(String[] args) throws Exception {
        LOGGER.info("API 抽取任务启动, args={}", maskArgs(args));
        String configJson = readConfig(args);
        ApiExtractJobConfig config = JsonUtils.read(configJson, ApiExtractJobConfig.class);
        LOGGER.info("API 抽取配置加载完成, jobId={}, triggerMode={}, sinkType={}, table={}",
                config.job == null ? null : config.job.id,
                config.trigger == null ? null : config.trigger.mode,
                config.sink == null ? null : config.sink.type,
                config.sink == null || config.sink.table == null ? null : config.sink.table.name);
        ApiExtractRunner runner = new DefaultApiExtractRunner();
        ApiExtractResult result = runner.run(config);
        LOGGER.info("API 抽取任务返回, result={}", JsonUtils.write(result));
        if (!result.isSuccess()) {
            System.exit(1);
        }
    }

    /**
     * 读取配置内容,支持从文件或 JSON 字符串读取.
     *
     * @param args 命令行参数
     * @return 配置 JSON 字符串
     * @throws Exception 读取失败时抛出
     */
    private static String readConfig(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing --config <path> or --config-json <json>");
        }
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                return Files.readString(Path.of(args[i + 1]));
            }
            if ("--config-json".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        throw new IllegalArgumentException("Missing --config <path> or --config-json <json>");
    }

    private static String maskArgs(String[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        String[] masked = args.clone();
        for (int i = 0; i < masked.length; i++) {
            if ("--config-json".equals(masked[i]) && i + 1 < masked.length) {
                masked[i + 1] = "<json>";
            }
        }
        return java.util.Arrays.toString(masked);
    }
}
