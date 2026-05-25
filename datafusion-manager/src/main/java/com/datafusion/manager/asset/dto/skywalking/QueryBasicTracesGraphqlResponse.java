package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * QueryBasicTracesGraphqlResponse.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class QueryBasicTracesGraphqlResponse {
    
    /**
     * data(匹配顶层 JSON 结构).
     */
    private QueryBasicTracesData data;
}
