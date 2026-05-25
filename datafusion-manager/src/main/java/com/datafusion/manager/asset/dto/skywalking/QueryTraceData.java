package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * QueryTraceData.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/22
 * @since 2025/10/22
 */
@Data
public class QueryTraceData {
    
    /**
     * queryTrace(字段名必须是 queryTrace).
     */
    private QueryTraceResult queryTrace;
}
