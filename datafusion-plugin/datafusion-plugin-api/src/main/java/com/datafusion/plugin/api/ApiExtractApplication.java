package com.datafusion.plugin.api;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ApiExtractResult;
import com.datafusion.plugin.api.core.ApiExtractRunner;
import com.datafusion.plugin.api.core.DefaultApiExtractRunner;
import com.datafusion.plugin.api.util.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

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
    private static final Logger LOGGER = Logger.getLogger(ApiExtractApplication.class.getName());
    
    /**
     * 启动 API 抽取任务.
     *
     * @param args 命令行参数
     * @throws Exception 启动失败时抛出
     */
    public static void main(String[] args) throws Exception {
        String configJson = readConfig(args);
        ApiExtractJobConfig config = JsonUtils.read(configJson, ApiExtractJobConfig.class);
        ApiExtractRunner runner = new DefaultApiExtractRunner();
        ApiExtractResult result = runner.run(config);
        LOGGER.info(JsonUtils.write(result));
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
}
