package com.datafusion.manager.metadata.support.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * 字段信息标准化模型.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/9/12
 * @since 2023/9/12
 */
@Data
public class TableColumnInfo implements Serializable {
    
    private static final long serialVersionUID = -593823848413248529L;
    
    /**
     * 主键.
     */
    private UUID columnId;
    
    /**
     * 目标表元数据信息表关联键.
     */
    private UUID tableId;
    
    /**
     * 表名称.
     */
    private String tableName;
    
    /**
     * 字段序号.
     */
    private Integer columnSerial;
    
    /**
     * 字段名称.
     */
    private String columnName;
    
    /**
     * 字段注释.
     */
    private String columnDesc;
    
    /**
     * 存储类型.
     */
    private String columnType;
    
    /**
     * 字段的Java类型.
     */
    private String javaType;
    
    /**
     * 字段的查询类型.
     */
    private String viewType;
    
    /**
     * 默认值.
     */
    private String defaultValue;
    
    /**
     * 是否非空.
     */
    private Boolean isNullable;
    
    /**
     * 是否主键.
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
     * 数字类小数位.
     */
    @TableField("scale")
    private Integer scale;
    
    /**
     * 表类型.
     */
    private String tableType;
    
    /**
     * 原始字段类型.
     */
    private String fullColumnType;
}
