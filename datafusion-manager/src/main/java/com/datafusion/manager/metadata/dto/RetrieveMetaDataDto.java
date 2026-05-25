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
import java.util.List;
import java.util.UUID;

/**
 * 元数据同步Dto.
 *
 * @author david
 * @version 3.6.4, 2024/9/4
 * @since 3.6.4, 2024/9/4
 */
@Data
@Schema(name = "RetrieveMetaDataDto", description = "元数据同步Dto")
public class RetrieveMetaDataDto {

    /**
     * 数据源ID.
     */
    @Schema(name = "datasourceId", description = "数据源ID")
    @NotNull(message = "datasourceId不能为空")
    private UUID datasourceId;

    /**
     * 数据表名称集合.
     */
    @Schema(name = "tableNames", description = "数据表名称集合")
    @NotEmpty(message = "tableNames不能为空")
    private List<String> tableNames;
}
