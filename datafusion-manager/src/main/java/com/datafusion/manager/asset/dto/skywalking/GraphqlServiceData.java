package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

import java.util.List;

/**
 * GraphqlServiceData.
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class GraphqlServiceData {
    
    /**
     * 所有服务列表.
     */
    private List<SkyWalkingServiceDto> getAllServices;
    
}
