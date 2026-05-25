package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * api资源导入实体.
 *
 * @author zhengjiexiang
 * @version 1.0.0, 2026/2/26
 * @since 2026/2/26
 */
@Data
public class ApiResourceResp {

    /**
     * 资源名称.
     */
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;

    /**
     * 组织名称.
     */
    @Schema(name = "organization", description = "组织名称")
    private String organization;

    /**
     * 业务域.
     */
    @Schema(name = "businessDomain", description = "业务域")
    private String businessDomain;

    /**
     * 环境.
     */
    @Schema(name = "env", description = "环境")
    private String env;

    /**
     * 服务类型.
     */
    @Schema(name = "serviceType", description = "服务类型")
    private String serviceType;

    /**
     * 服务英文名称.
     */
    @Schema(name = "serviceEnName", description = "服务英文名称")
    private String serviceEnName;

    /**
     * 请求方式.
     */
    @Schema(name = "requestType", description = "请求方式")
    private String requestType;

    /**
     * 接口地址url.
     */
    @Schema(name = "requestUrl", description = "接口地址url，不需要拼接basePath")
    private String requestUrl;

    /**
     * 接口basePath.
     */
    @Schema(name = "basePath", description = "basePath,如/api/openapi")
    private String basePath;

}
