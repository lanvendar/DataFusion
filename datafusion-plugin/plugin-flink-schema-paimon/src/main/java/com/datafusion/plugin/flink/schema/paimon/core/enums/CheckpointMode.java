package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

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
        String text = TextUtils.upper(value, EXACTLY_ONCE.name());
        try {
            return CheckpointMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported checkpointMode: " + value, e);
        }
    }
}
