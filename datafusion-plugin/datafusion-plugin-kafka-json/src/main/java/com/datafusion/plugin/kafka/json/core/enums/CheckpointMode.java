package com.datafusion.plugin.kafka.json.core.enums;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

/**
 * Flink checkpoint 模式.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum CheckpointMode {

    /**
     * 精确一次.
     */
    EXACTLY_ONCE,

    /**
     * 至少一次.
     */
    AT_LEAST_ONCE;

    /**
     * 解析 checkpoint 模式.
     *
     * @param value 配置值
     * @return checkpoint 模式
     */
    public static CheckpointMode parse(String value) {
        String text = TextUtils.upper(value, AT_LEAST_ONCE.name());
        try {
            return CheckpointMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new KafkaJsonPaimonException("Unsupported checkpointMode: " + value, e);
        }
    }
}
