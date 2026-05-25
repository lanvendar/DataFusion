package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * QueryTraceGraphqlResponse.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/22
 * @since 2025/10/22
 */
@Data
public class QueryTraceGraphqlResponse {
    
    /**
     * data.
     */
    private QueryTraceData data;
}
