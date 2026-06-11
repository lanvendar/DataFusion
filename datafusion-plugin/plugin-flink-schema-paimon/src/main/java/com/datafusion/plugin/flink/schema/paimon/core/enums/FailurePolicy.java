package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

/**
 * 消息失败处理策略.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum FailurePolicy {

    /**
     * 失败即停止.
     */
    FAIL_FAST;

    /**
     * 解析失败策略.
     *
     * @param value 配置值
     * @return 失败策略
     */
    public static FailurePolicy parse(String value) {
        String text = TextUtils.upper(value, FAIL_FAST.name());
        try {
            return FailurePolicy.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported failurePolicy: " + value, e);
        }
    }
}
