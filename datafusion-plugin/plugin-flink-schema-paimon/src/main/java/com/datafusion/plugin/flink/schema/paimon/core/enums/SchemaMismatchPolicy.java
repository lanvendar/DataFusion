package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

/**
 * Paimon 表结构不匹配处理策略.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum SchemaMismatchPolicy {

    /**
     * 跳过不兼容表写入.
     */
    SKIP,

    /**
     * 失败即停止.
     */
    FAIL;

    /**
     * 解析表结构不匹配处理策略.
     *
     * @param value 配置值
     * @return 表结构不匹配处理策略
     */
    public static SchemaMismatchPolicy parse(String value) {
        String text = TextUtils.upper(value, SKIP.name());
        try {
            return SchemaMismatchPolicy.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported schemaMismatchPolicy: " + value, e);
        }
    }
}
