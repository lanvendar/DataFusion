package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

import java.util.List;

/**
 * BasicTraceDto(匹配 traces 数组中的单个链路记录).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class BasicTraceDto {
    
    /**
     * traceIds.
     */
    private List<String> traceIds;
    
    /**
     * segmentId.
     */
    private String segmentId;
    
    /**
     * endpointNames.
     */
    private List<String> endpointNames;
    
    /**
     * duration(持续时间，单位 ms).
     */
    private Integer duration;
    
    /**
     * start(开始时间戳 (String or Long)).
     */
    private String start;
    
    /**
     * isError.
     */
    private Boolean isError;
}
