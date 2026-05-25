package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/20
 * @since 2025/10/20
 */
@Data
public class AssetEdgeNodeDto {
    
    /**
     * 血缘关系起点urn.
     */
    @Schema(name = "sourceUrn", description = "源节点urn")
    private String sourceUrn;
    
    /**
     * 血缘起点名称.
     */
    @Schema(name = "sourceNodeName", description = "源节点名称")
    private String sourceNodeName;
    
    /**
     * 源节点类型.
     */
    @Schema(name = "sourceNodeType", description = "源节点类型")
    private String sourceNodeType;
    
    /**
     * 源节点子类型.
     */
    @Schema(name = "sourceSubNodeType", description = "源节点子类型")
    private String sourceSubNodeType;
    
    /**
     * 血缘关系终点urn.
     */
    @Schema(name = "targetUrn", description = "目标节点urn")
    private String targetUrn;
    
    /**
     * 血缘终点名称.
     */
    @Schema(name = "targetNodeName", description = "目标节点名称")
    private String targetNodeName;
    
    /**
     * 源节点类型.
     */
    @Schema(name = "targetNodeType", description = "目标节点类型")
    private String targetNodeType;
    
    /**
     * 源节点子类型.
     */
    @Schema(name = "targetSubNodeType", description = "目标节点子类型")
    private String targetSubNodeType;
    
}
