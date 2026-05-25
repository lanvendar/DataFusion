package com.datafusion.manager.metadata.support.model;

import lombok.Data;


/**
 * TableColumnInfo 元数据结构结果对象.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/2/10
 * @since 2023/2/10
 */
@Data
public class PostgresTableColumn {
    
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
     * 字段类型.
     */
    private String dataType;
    
    /**
     * 字段类型.
     */
    private String columnType;
    
    /**
     * 字段默认值.
     */
    private String defaultValue;
    
    /**
     * 字段是否可空.
     */
    private Boolean isNullable;
    
    /**
     * 字段是否是主键.
     */
    private Boolean isPrimary;

    /**
     * 表的主键.
     */
    private String primaryKeys;
    
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
}
