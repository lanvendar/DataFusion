package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 指标实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class MetricsResp {
    
    /**
     * 指标编码.
     */
    @Schema(name = "metricCode", description = "指标编码")
    private String metricCode;

    /**
     * 指标名称.
     */
    @Schema(name = "metricName", description = "指标名称")
    private String metricName;

    /**
     * 时间维度（日/月/年）.
     */
    @Schema(name = "dimension", description = "时间维度（日/月/年）")
    private String dimension;

    /**
     * 字段名称.
     */
    @Schema(name = "columnName", description = "列名称")
    private String columnName;

    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;
}
