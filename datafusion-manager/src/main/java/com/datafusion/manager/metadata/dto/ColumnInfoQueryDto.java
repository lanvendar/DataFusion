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

import java.util.UUID;

/**
 * 表字段信息查询Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/26
 * @since 3.6.4, 2024/8/26
 */
@Data
@Schema(name = "ColumnInfoQueryDto", description = "表字段信息查询Dto")
public class ColumnInfoQueryDto {

    /**
     * 表ID.
     */
    @Schema(name = "tableId", description = "表ID")
    private UUID tableId;

    /**
     * 字段名称.
     */
    @Schema(name = "columnName", description = "字段名称")
    private String columnName;

}
