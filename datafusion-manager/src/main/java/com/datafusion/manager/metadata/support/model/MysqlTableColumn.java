package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.math.BigInteger;

/**
 * TableColumnInfo 元数据结构结果对象.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/2/10
 * @since 2023/2/10
 */
@Data
public class MysqlTableColumn {
    
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
    private BigInteger ordinalPosition;
    
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
    private BigInteger columnLength;
    
    /**
     * 字段精度.
     */
    private BigInteger columnPrecision;
    
    /**
     * 表是否是视图.
     */
    private Integer isView;
    
    /**
     * 视图定义语句.
     */
    private String viewDef;
}
