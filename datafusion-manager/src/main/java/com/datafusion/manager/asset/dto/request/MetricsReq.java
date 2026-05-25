package com.datafusion.manager.asset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 指标实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class MetricsReq {
    
    /**
     * 指标编码.
     */
    @NotNull(message = "指标编码不能为空")
    @Schema(name = "metricCode", description = "指标编码")
    private String metricCode;

    /**
     * 指标名称.
     */
    @NotNull(message = "指标名称不能为空")
    @Schema(name = "metricName", description = "指标名称")
    private String metricName;

    /**
     * 时间维度（日/月/年）.
     */
    @NotNull(message = "时间维度（日/月/年）不能为空")
    @Schema(name = "dimension", description = "时间维度（日/月/年）")
    private String dimension;

    /**
     * 字段名称.
     */
    @NotNull(message = "列名称不能为空")
    @Schema(name = "columnName", description = "列名称")
    private String columnName;

    /**
     * 表名称.
     */
    @NotNull(message = "表名称不能为空")
    @Schema(name = "tableName", description = "表名称")
    private String tableName;

    /**
     * 数据源名称.
     */
    @NotNull(message = "数据源名称不能为空")
    @Schema(name = "datasourceName", description = "数据源名称")
    private String datasourceName;
}
