package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Data
public class EdgeVo {
    
    /**
     * 来源节点urn.
     */
    @Schema(name = "sourceUrn", description = "来源节点urn")
    private String sourceUrn;
    
    /**
     * 目标节点urn.
     */
    @Schema(name = "targetUrn", description = "目标节点urn")
    private String targetUrn;
}
