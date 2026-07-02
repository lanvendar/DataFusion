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
     * 默认任务配置文件.
     */
    private static final String DEFAULT_JOB_FILE = "api-job.json";

    /**
     * 任务配置文件参数名.
     */
    private static final String JOB_OPTION = "-job";

    /**
     * 启动 API 抽取任务.
     *
     * @param args 命令行参数
     * @throws Exception 启动失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Path jobFile = resolveJobFile(args);
        LOGGER.info("API 抽取任务启动, jobFile={}", jobFile);
        String configJson = Files.readString(jobFile);
        ApiExtractJobConfig config = JsonUtils.read(configJson, ApiExtractJobConfig.class);
        LOGGER.info("API 抽取配置加载完成, jobId={}, sinkType={}, table={}",
                config.job == null ? null : config.job.id,
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
     * 解析任务配置文件路径.
     *
     * @param args 命令行参数
     * @return 任务配置文件路径
     */
    private static Path resolveJobFile(String[] args) {
        if (args == null || args.length == 0) {
            return Path.of(DEFAULT_JOB_FILE);
        }
        for (int i = 0; i < args.length; i++) {
            if (JOB_OPTION.equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(JOB_OPTION + " 参数不能为空");
                }
                return Path.of(args[i + 1]);
            }
        }
        return Path.of(DEFAULT_JOB_FILE);
    }
}
