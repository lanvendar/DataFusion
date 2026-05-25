package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 资源名称信息.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/2/12
 * @since 2026/2/12
 */
@Data
public class ApiResourceNameResp {

    /**
     * 资源ID.
     */
    @Schema(name = "resourceId", description = "资源ID")
    private UUID resourceId;

    /**
     * 应用名称.
     */
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;

    /**
     * 请求url.
     */
    @Schema(name = "requestUrl", description = "url")
    private String requestUrl;

    /**
     * 请求类型.
     */
    @Schema(name = "requestType", description = "请求类型")
    private String requestType;

}
