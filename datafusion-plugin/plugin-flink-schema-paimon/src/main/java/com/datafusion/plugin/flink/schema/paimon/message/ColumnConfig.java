package com.datafusion.plugin.flink.schema.paimon.message;

import java.io.Serializable;

/**
 * Kafka schema 字段定义.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ColumnConfig implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 字段名.
     */
    public String name;

    /**
     * 字段类型.
     */
    public String type = "STRING";

    /**
     * 字符串长度.
     */
    public Integer length;

    /**
     * 数值精度.
     */
    public Integer precision;

    /**
     * 数值小数位.
     */
    public Integer scale;

    /**
     * 是否允许为空.
     */
    public Boolean nullable = true;

    /**
     * 默认值.
     */
    public Object defaultValue;

    /**
     * 字段注释.
     */
    public String comment;

    /**
     * 日期时间格式.
     */
    public String format;

    /**
     * 复制字段定义.
     *
     * @return 字段定义副本
     */
    public ColumnConfig copy() {
        ColumnConfig copy = new ColumnConfig();
        copy.name = name;
        copy.type = type;
        copy.length = length;
        copy.precision = precision;
        copy.scale = scale;
        copy.nullable = nullable;
        copy.defaultValue = defaultValue;
        copy.comment = comment;
        copy.format = format;
        return copy;
    }
}
