package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * SpanRefDto(链路追踪 Span 内部的 Ref 键值对).
 *
 * @author zhengjiexiang
 * @version 1.0.0, 2026/01/28
 * @since 2026/01/28
 */
@Data
public class SpanRefDto {
    
    /**
     * key.
     */
    private String parentSegmentId;
    
    /**
     * value.
     */
    private String parentSpanId;
}
