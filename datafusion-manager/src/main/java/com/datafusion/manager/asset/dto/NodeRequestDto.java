package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/20
 * @since 2025/10/20
 */
@Data
public class NodeRequestDto {
    
    /**
     * 资源名称.
     */
    @Schema(name = "resourceId", description = "资源Id")
    private UUID resourceId;
    
    /**
     * 资源类型.
     */
    @Schema(name = "resourceType", description = "资源类型")
    private String resourceType;
    
    /**
     * 资源标签.
     */
    @Schema(name = "resourceTag", description = "资源标签")
    private String resourceTag;
}
