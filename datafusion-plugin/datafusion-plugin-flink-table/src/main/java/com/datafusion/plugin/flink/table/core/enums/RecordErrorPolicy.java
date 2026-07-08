package com.datafusion.plugin.flink.table.core.enums;

import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.util.TextUtils;

/**
 * Paimon 单条记录错误处理策略.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum RecordErrorPolicy {

    /**
     * 跳过错误记录.
     */
    SKIP,

    /**
     * 失败即停止.
     */
    FAIL;

    /**
     * 解析单条记录错误处理策略.
     *
     * @param value 配置值
     * @return 单条记录错误处理策略
     */
    public static RecordErrorPolicy parse(String value) {
        String text = TextUtils.upper(value, SKIP.name());
        try {
            return RecordErrorPolicy.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkTableException("Unsupported recordErrorPolicy: " + value, e);
        }
    }
}
