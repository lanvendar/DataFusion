package com.datafusion.common.type;

/**
 * 数据类型大类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/20
 * @since 2025/3/20
 */
public enum DataTypeFamily {
    /**
     * 数值类型.
     */
    NUMERIC,
    /**
     * 文本类型.
     */
    TEXT,
    /**
     * 日期类型.
     */
    DATE,
    /**
     * 布尔类型.
     */
    BOOLEAN,
    /**
     * 二进制类型.
     */
    BINARY,
    /**
     * UUID类型.
     */
    UUID,
    /**
     * 对象类型.
     */
    OBJECT,
    /**
     * 数组类型.
     */
    ARRAY,
    /**
     * Map类型.
     */
    MAP,
    /**
     * JSON类型.
     */
    JSON;
}
