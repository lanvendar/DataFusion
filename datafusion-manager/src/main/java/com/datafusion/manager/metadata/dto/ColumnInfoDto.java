/*
 * Copyright © 2000-2024 Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 元数据-表字段Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/26
 * @since 3.6.4, 2024/8/26
 */
@Data
@Accessors(chain = true)
@Schema(name = "ColumnInfoDto", description = "表字段信息Dto")
public class ColumnInfoDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 表ID.
     */
    @Schema(name = "tableId", description = "表ID")
    private UUID tableId;

    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;

    /**
     * 字段序号.
     */
    @Schema(name = "columnSerial", description = "字段序号")
    private Integer columnSerial;

    /**
     * 字段名称.
     */
    @Schema(name = "columnName", description = "字段名称")
    private String columnName;

    /**
     * 字段注释.
     */
    @Schema(name = "columnDesc", description = "字段注释")
    private String columnDesc;

    /**
     * 字段类型.
     */
    @Schema(name = "columnType", description = "字段类型")
    private String columnType;

    /**
     * 字段长度.
     */
    @Schema(name = "columnLength", description = "字段长度")
    private Integer columnLength;

    /**
     * 字段精度.
     */
    @Schema(name = "columnPrecision", description = "字段精度")
    private Integer columnPrecision;

    /**
     * 字段精度.
     */
    @Schema(name = "scale", description = "数字类精度")
    private Integer scale;

    /**
     * 是否主键.
     */
    @Schema(name = "isPrimary", description = "是否主键")
    private Boolean isPrimary;

    /**
     * 是否非空.
     */
    @Schema(name = "isNullable", description = "是否非空")
    private Boolean isNullable;

    /**
     * 默认值.
     */
    @Schema(name = "defaultValue", description = "默认值")
    private String defaultValue;

    /**
     * 对应java类型.
     */
    @Schema(name = "javaType", description = "对应java类型")
    private String javaType;

    /**
     * 查询类型.
     */
    @Schema(name = "viewType", description = "查询类型")
    private String viewType;
}
