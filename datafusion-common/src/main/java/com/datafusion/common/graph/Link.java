package com.datafusion.common.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 边集合定义.
 *
 * @param <K> 点id
 * @author lanvendar
 * @version 3.0.0, 2023/8/30
 * @since 2023/8/30
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Link<K> {
    
    /**
     * link id.
     */
    private String id;
    
    /**
     * 开始节点 id.
     */
    private K startId;
    
    /**
     * 终止节点 id.
     */
    private K endId;
}
