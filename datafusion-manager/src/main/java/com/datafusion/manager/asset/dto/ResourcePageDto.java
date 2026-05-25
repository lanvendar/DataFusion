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
public class ResourcePageDto {
    
    /**
     * 资源名称.
     */
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;
    
    /**
     * 资源类型.
     */
    @Schema(name = "resourceType", description = "资源类型")
    private String resourceType;
    
    /**
     * 状态.
     */
    @Schema(name = "status", description = "状态")
    private Integer status;
    
}
