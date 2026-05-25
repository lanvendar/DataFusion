package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * SkyWalkingServiceQueryDto.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class SkyWalkingServiceQueryDto {
    
    /**
     * 开始时间.
     */
    private String start;
    
    /**
     * 结束时间.
     */
    private String end;
    
    /**
     * step.
     */
    private String step;
}
