package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

import java.util.List;

/**
 * FindEndpointData.
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class FindEndpointData {
    
    /**
     * findEndpoint.
     */
    private List<EndpointDto> findEndpoint;
}
