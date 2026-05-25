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

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 元数据-表新增Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/26
 * @since 3.6.4, 2024/8/26
 */
@Data
@Schema(name = "TableInfoUpdateDto", description = "元数据-表新增Dto")
public class TableInfoUpdateDto  {
    
    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    @NotNull(message = "id不能为空")
    private UUID id;
    
    /**
     * 数据源id.
     */
    @Schema(name = "datasourceId", description = "数据源id")
    @NotNull(message = "数据源id")
    private UUID datasourceId;
    
    /**
     * 数据源id.
     */
    @Schema(name = "tableName", description = "表名称")
    @NotNull(message = "表名称")
    private String tableName;
    
    /**
     * 表注释.
     */
    @Schema(name = "tableDesc", description = "表注释")
    private String tableDesc;

    /**
     * 表所属目录.
     */
    @Schema(name = "catalogName", description = "表所属目录")
    private String catalogName;
    
    /**
     * 主键列表.
     */
    @Schema(name = "primaryKeys", description = "主键列表")
    private String primaryKeys;
    
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
     * 是否视图.
     */
    @Schema(name = "viewDef", description = "视图定义")
    private Boolean isView;

    /**
     * 视图定义.
     */
    @TableField("viewDef")
    private String viewDef;
    
    /**
     * 表属性.
     */
    @Schema(name = "tableProperties", description = "表属性")
    @JsonSetter(nulls = Nulls.SKIP)
    private JsonNode tableProperties;
}
