package com.datafusion.manager.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/20
 * @since 2025/10/20
 */
@Data
public class AssetNodeDto {
    
    /**
     * 节点urn.
     */
    private String nodeUrn;
    
    /**
     * 节点名称.
     */
    private String nodeName;
    
    /**
     * 节点类型.
     */
    private String nodeType;
    
    /**
     * 节点子类型.
     */
    private String nodeSubType;
    
    /**
     * 节点属性.
     */
    private JsonNode nodeProp;
    
}
