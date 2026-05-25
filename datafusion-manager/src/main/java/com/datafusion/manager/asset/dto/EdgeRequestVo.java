package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/4/7
 * @since 2026/4/7
 */
@Data
public class EdgeRequestVo {
    /**
     * 数据源名称.
     */
    @Schema(name = "datasourceName", description = "数据源名称")
    @NotNull(message = "数据源名称不能为空")
    private String datasourceName;

    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    @NotNull(message = "表名称不能为空")
    private String tableName;

    /**
     * 字段名称.
     */
    @Schema(name = "columnName", description = "字段名称")
    @NotNull(message = "字段名称不能为空")
    private String columnName;

}
