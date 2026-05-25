package com.datafusion.manager.metadata.support.model;

import lombok.Data;

/**
 * Starrocks元数据结构结果对象.
 *
 * @author chengtg
 * @version 3.7.4, 2024/11/28
 * @since 3.7.4, 2024/11/28
 */
@Data
public class StarrocksTableColumn {
    
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
    private Integer ordinalPosition;
    
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
    private Boolean isNullable;
    
    /**
     * 字段是否是主键.
     */
    private Boolean isPrimary;
    
    /**
     * 字段长度.
     */
    private Integer columnLength;
    
    /**
     * 字段精度.
     */
    private Integer columnPrecision;

    /**
     * 字段小数位.
     */
    private Integer scale;
    
    /**
     * 表是否是视图.
     */
    private Boolean isView;
    
    /**
     * 视图定义语句.
     */
    private String viewDef;

    /**
     * 分区键.
     */
    private String partitionKey;

    /**
     * 数据分桶键.
     */
    private String bucketKey;

    /**
     * 表属性.
     */
    private String properties;

    /**
     * 分桶数量.
     */
    private String distributeBucket;

    /**
     * tableModel.
     */
    private String tableModel;

    /**
     * 直接获取primary key.
     */
    private String primaryKey;

    /**
     * 直接获取index_key.
     */
    private String sortKey;
    
    /**
     * 原始字段类型.
     */
    private String fullColumnType;

}
