package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

/**
 * Flink state backend 类型.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum StateBackendType {

    /**
     * HashMap backend.
     */
    HASHMAP,

    /**
     * RocksDB backend.
     */
    ROCKSDB;

    /**
     * 解析 state backend 类型.
     *
     * @param value 配置值
     * @return state backend 类型
     */
    public static StateBackendType parse(String value) {
        String text = TextUtils.upper(value, HASHMAP.name());
        try {
            return StateBackendType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported stateBackend: " + value, e);
        }
    }
}
