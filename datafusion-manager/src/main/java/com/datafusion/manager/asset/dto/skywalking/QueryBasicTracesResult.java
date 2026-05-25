package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

import java.util.List;

/**
 * QueryBasicTracesResult(匹配 queryBasicTraces 字段的内容).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class QueryBasicTracesResult {
    
    /**
     * traces.
     */
    private List<BasicTraceDto> traces;
}
