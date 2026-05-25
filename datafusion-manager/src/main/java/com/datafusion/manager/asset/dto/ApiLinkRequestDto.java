package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * API血缘解析实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/27
 * @since 2025/10/27
 */
@Data
public class ApiLinkRequestDto {
    
    /**
     * 服务英文名称.
     */
    @NotNull(message = "服务英文名称不能为空")
    @Schema(name = "serviceEnName", description = "服务英文名称")
    private String serviceEnName;
    
    /**
     * 时间范围类型.
     */
    @Schema(name = "timeRangeType", description = "时间范围类型(0:小时;1:天;2:周)")
    private int timeRangeType;
    
    /**
     * 服务英文名称.
     */
    @Schema(name = "timeRangeNum", description = "时间跨度范围(默认是)")
    private int timeRangeNum = 1;
    
}
