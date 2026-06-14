package com.datafusion.plugin.kafka.json.config;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作业配置加载器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConfigLoader {

    /**
     * 环境变量占位符.
     */
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)}");

    /**
     * 加载配置文件.
     *
     * @param configPath 配置路径
     * @return 作业配置
     */
    public KafkaJsonPaimonJobConfig load(String configPath) {
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new KafkaJsonPaimonException("Config path is required");
        }
        try {
            String content = Files.readString(Path.of(configPath), StandardCharsets.UTF_8);
            return JacksonUtils.str2Bean(resolveEnv(content), KafkaJsonPaimonJobConfig.class);
        } catch (IOException e) {
            throw new KafkaJsonPaimonException("Failed to load config: " + configPath, e);
        }
    }

    /**
     * 从 JSON 字符串加载配置.
     *
     * @param content JSON 内容
     * @return 作业配置
     */
    public KafkaJsonPaimonJobConfig loadContent(String content) {
        try {
            return JacksonUtils.str2Bean(resolveEnv(content), KafkaJsonPaimonJobConfig.class);
        } catch (IOException e) {
            throw new KafkaJsonPaimonException("Failed to parse config content", e);
        }
    }

    private String resolveEnv(String content) {
        Matcher matcher = ENV_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String value = System.getenv(envName);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
