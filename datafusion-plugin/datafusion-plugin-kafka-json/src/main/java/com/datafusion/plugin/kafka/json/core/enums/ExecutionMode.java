package com.datafusion.plugin.kafka.json.core.enums;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

/**
 * Flink 执行模式.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ExecutionMode {

    /**
     * 流模式.
     */
    STREAMING,

    /**
     * 批模式.
     */
    BATCH;

    /**
     * 解析执行模式.
     *
     * @param value 配置值
     * @return 执行模式
     */
    public static ExecutionMode parse(String value) {
        String text = TextUtils.upper(value, STREAMING.name());
        try {
            return ExecutionMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new KafkaJsonPaimonException("Unsupported executionMode: " + value, e);
        }
    }
}
