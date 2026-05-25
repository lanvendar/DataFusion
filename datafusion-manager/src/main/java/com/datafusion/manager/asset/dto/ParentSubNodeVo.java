package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Data
public class ParentSubNodeVo {
    
    /**
     * 节点名称.
     */
    @Schema(name = "nodeName", description = "节点名称")
    private String nodeName;
    
    /**
     * 节点urn.
     */
    @Schema(name = "nodeUrn", description = "节点urn")
    private String nodeUrn;
    
    /**
     * 子节点.
     */
    @Schema(name = "subNodes", description = "子节点")
    List<ParentSubNodeVo> subNodes;
    
}
