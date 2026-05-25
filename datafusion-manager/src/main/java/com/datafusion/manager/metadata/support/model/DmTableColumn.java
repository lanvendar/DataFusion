package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DMTableColumn 元数据结构结果对象.
 *
 * @author zhuli
 * @version 3.1.0, 2023/4/11
 * @since 2023/4/1
 */
@Data
public class DmTableColumn {

    /**
     * 表空间名称.
     */
    private String tableSchema;

    /**
     * 表名称.
     */
    private String tableName;

    /**
     * 表描述.
     */
    private String tableDesc;

    /**
     * 字段索引序号.
     */
    private BigDecimal ordinalPosition;

    /**
     * 字段名称.
     */
    private String columnName;

    /**
     * 字段描述.
     */
    private String columnDesc;

    /**
     * 字段类型详细信息(不常用).
     */
    private String dataType;

    /**
     * 字段类型.
     */
    private String columnType;

    /**
     * 字段默认值.
     */
    private String columnDefault;

    /**
     * 字段是否可空.
     */
    private Integer isNullable;

    /**
     * 字段是否是主键.
     */
    private Integer isPrimary;

    /**
     * 字段长度.
     */
    private BigDecimal columnLength;

    /**
     * 字段精度.
     */
    private BigDecimal columnPrecision;

    /**
     * 字段精度.
     */
    private Integer scale;

    /**
     * 表是否是视图.
     */
    private Integer isView;

    /**
     * 视图定义语句.
     */
    private String viewDef;

    /**
     * 表级主键.
     */
    private String tablePrimaryKeys;
    
    /**
     * 原始字段类型.
     */
    private String fullColumnType;
}
