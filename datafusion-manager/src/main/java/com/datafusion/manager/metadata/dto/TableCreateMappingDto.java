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

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 源表/目标表映射关系DTO.
 *
 * @author david
 * @version 3.6.4, 2024/9/11
 * @since 3.6.4, 2024/9/11
 */
@Data
@Schema(name = "TableCreateMappingDto", description = "源表/目标表映射关系DTO")
public class TableCreateMappingDto {

    /**
     * 源表ID.
     */
    @Schema(name = "sourceTableId", description = "源表ID")
    @NotNull(message = "sourceTableId不能为空")
    private UUID sourceTableId;

    /**
     * 原表名称.
     */
    @Schema(name = "sourceTableName", description = "源表名称")
    @NotEmpty(message = "sourceTableName不能为空")
    private String sourceTableName;

    /**
     * 目标表名称.
     */
    @Schema(name = "targetTableName", description = "目标表名称")
    @NotEmpty(message = "targetTableName不能为空")
    private String targetTableName;

    /**
     * 是否建表成功.
     */
    @Schema(name = "created", description = "是否建表成功, 请求时无需传入")
    private boolean created;

    /**
     * 信息.
     */
    @Schema(name = "message", description = "信息, 失败时有值, 请求时无需传入")
    private String message;
}
