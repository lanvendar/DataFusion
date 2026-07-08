package com.datafusion.plugin.flink.table.core.enums;

import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.util.TextUtils;

/**
 * Paimon 主键模式.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum PrimaryKeyMode {

    /**
     * 字段主键.
     */
    FIELDS,

    /**
     * 代理主键.
     */
    PROXY;

    /**
     * 解析主键模式.
     *
     * @param value 配置值
     * @return 主键模式
     */
    public static PrimaryKeyMode parse(String value) {
        String text = TextUtils.upper(value, FIELDS.name());
        try {
            return PrimaryKeyMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkTableException("Unsupported primaryKeys.mode: " + value, e);
        }
    }
}
