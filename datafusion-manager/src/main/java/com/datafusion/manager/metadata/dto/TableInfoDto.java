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

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 表信息Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/26
 * @since 3.6.4, 2024/8/26
 */
@Data
@Schema(name = "TableInfoDto", description = "表信息Dto")
public class TableInfoDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;
    
    /**
     * 数据连接名称.
     */
    @Schema(name = "databaseConnectName", description = "数据连接名称")
    private String databaseConnectName;

    /**
     * 数据源连接ID.
     */
    @Schema(name = "datasourceId", description = "数据源连接ID")
    private UUID datasourceId;

    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;

    /**
     * 表注释.
     */
    @Schema(name = "tableDesc", description = "表注释")
    private String tableDesc;

    /**
     * 索引键列表.
     */
    @Schema(name = "indexKeys", description = "索引键列表")
    private String indexKeys;

    /**
     * 分区键列表.
     */
    @Schema(name = "partitionKeys", description = "分区键列表")
    private String partitionKeys;

    /**
     * 分桶键列表.
     */
    @Schema(name = "bucketKeys", description = "分桶键列表")
    private String bucketKeys;

    /**
     * 主键列表.
     */
    @Schema(name = "primaryKeys", description = "主键列表")
    private String primaryKeys;
    
    /**
     * 规则.
     */
    @Schema(name = "compressionRules", description = "规则")
    private String compressionRules;

    /**
     * 表属性.
     */
    @Schema(name = "tableProperties", description = "表属性")
    private JsonNode tableProperties;

    /**
     * 是否可修改.
     */
    @Schema(name = "isModify", description = "允许修改")
    private Boolean isModify;

    /**
     * 是否视图.
     */
    @Schema(name = "isView", description = "是否视图")
    private Boolean isView;

    /**
     * 视图定义.
     */
    @Schema(name = "viewDef", description = "视图定义")
    private String viewDef;

    /**
     * 表类型.
     */
    @Schema(name = "tableType", description = "表类型")
    private String tableType;

    /**
     * 数据库类型.
     */
    @Schema(name = "databaseType", description = "数据库类型")
    private String databaseType;

    /**
     * 数据库schema名称.
     */
    @Schema(name = "schemaName", description = "数据库schema名称")
    private String schemaName;

    /**
     * 数据库名称.
     */
    @Schema(name = "databaseName", description = "数据库名称")
    private String databaseName;

    /**
     * 表同步检查时间.
     */
    @Schema(name = "checkTime", description = "表同步检查时间")
    protected LocalDateTime checkTime;

    /**
     * 创建时间.
     */
    @Schema(name = "createTime", description = "创建时间")
    protected LocalDateTime createTime;

    /**
     * 更新时间.
     */
    @Schema(name = "updateTime", description = "更新时间")
    protected LocalDateTime updateTime;

    /**
     * 表结构是否一致.
     */
    @Schema(name = "isEqual", description = "是否视图")
    private Boolean isEqual;
}
