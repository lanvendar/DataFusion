package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * QueryBasicTracesData(匹配 data 字段的内容).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class QueryBasicTracesData {
    
    /**
     * queryBasicTraces.
     */
    private QueryBasicTracesResult queryBasicTraces;
}
