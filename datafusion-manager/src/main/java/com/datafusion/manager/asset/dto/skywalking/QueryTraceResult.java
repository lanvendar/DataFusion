package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

import java.util.List;

/**
 * QueryTraceResult(匹配 queryTrace 字段的内容 (包含 spans 列表)).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/22
 * @since 2025/10/22
 */
@Data
public class QueryTraceResult {
    
    /**
     * spans.
     */
    private List<SpanDto> spans;
}
