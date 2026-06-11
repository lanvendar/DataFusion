package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

/**
 * Flink 重启策略.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum RestartStrategyType {

    /**
     * 不重启.
     */
    NO_RESTART,

    /**
     * 固定延迟重启.
     */
    FIXED_DELAY,

    /**
     * 失败率重启.
     */
    FAILURE_RATE;

    /**
     * 解析重启策略.
     *
     * @param value 配置值
     * @return 重启策略
     */
    public static RestartStrategyType parse(String value) {
        String text = TextUtils.upper(value, FIXED_DELAY.name());
        try {
            return RestartStrategyType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported restartStrategy: " + value, e);
        }
    }
}
