package com.datafusion.manager.metadata.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 元数据-表字段实体.
 *
 * @author david
 * @version 3.6.4, 2024/8/22
 * @since 3.6.4, 2024/8/22
 */
@Data
@Accessors(chain = true)
@TableName("metadata_column_info")
@NoArgsConstructor
public class ColumnInfoEntity extends BaseIdEntity {

    /**
     * 表ID.
     */
    @TableField("table_id")
    private UUID tableId;

    /**
     * 表名称.
     */
    @TableField("table_name")
    private String tableName;

    /**
     * 字段序号.
     */
    @TableField("column_serial")
    private Integer columnSerial;

    /**
     * 字段名称.
     */
    @TableField("column_name")
    private String columnName;

    /**
     * 字段注释.
     */
    @TableField("column_desc")
    private String columnDesc;

    /**
     * 字段类型.
     */
    @TableField("column_type")
    private String columnType;

    /**
     * 字段长度.
     */
    @TableField("column_length")
    private Integer columnLength;

    /**
     * 字段精度.
     */
    @TableField("column_precision")
    private Integer columnPrecision;

    /**
     * 数字类小数位.
     */
    @TableField("scale")
    private Integer scale;

    /**
     * 是否主键.
     */
    @TableField("is_primary")
    private Boolean isPrimary;

    /**
     * 是否非空.
     */
    @TableField("is_nullable")
    private Boolean isNullable;

    /**
     * 默认值.
     */
    @TableField("default_value")
    private String defaultValue;

    /**
     * 对应java类型.
     */
    @TableField("java_type")
    private String javaType;

    /**
     * 查询类型.
     */
    @TableField("view_type")
    private String viewType;

    /**
     * ColumnInfoEntity.
     * 
     * @param columnName
     *            columnName
     * @param columnType
     *            columnType
     * @param columnDesc
     *            columnDesc
     */
    public ColumnInfoEntity(String columnName, String columnType, String columnDesc) {
        this.columnName = columnName;
        this.columnType = columnType;
        this.columnDesc = columnDesc;
    }

}
