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
public class ResourceDto {

    /**
     * 资源id.
     */
    @Schema(name = "resourceId", description = "资源id")
    private UUID resourceId;

    /**
     * 资源名称.
     */
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;

    /**
     * 资源名称.
     */
    @Schema(name = "requestUrl", description = "APIUrl")
    private String requestUrl;

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
