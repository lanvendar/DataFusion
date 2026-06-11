package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

/**
 * 未匹配表处理策略.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum UnmatchedTablePolicy {

    /**
     * 跳过消息.
     */
    SKIP,

    /**
     * 作业失败.
     */
    FAIL;

    /**
     * 解析策略.
     *
     * @param value 配置值
     * @return 策略
     */
    public static UnmatchedTablePolicy parse(String value) {
        String text = TextUtils.upper(value, SKIP.name());
        try {
            return UnmatchedTablePolicy.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported unmatchedTablePolicy: " + value, e);
        }
    }
}
