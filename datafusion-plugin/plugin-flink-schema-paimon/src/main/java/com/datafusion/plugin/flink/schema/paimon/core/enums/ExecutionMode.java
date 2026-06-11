package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

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
            throw new FlinkSchemaPaimonException("Unsupported executionMode: " + value, e);
        }
    }
}
