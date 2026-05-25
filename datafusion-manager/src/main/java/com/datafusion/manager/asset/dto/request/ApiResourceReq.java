package com.datafusion.manager.asset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * api资源导入实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class ApiResourceReq {

    /**
     * 组织名称.
     */
    @NotNull(message = "组织名称不能为空")
    @Schema(name = "organization", description = "组织名称")
    private String organization;

    /**
     * 业务域.
     */
    @NotNull(message = "业务域不能为空")
    @Schema(name = "businessDomain", description = "业务域")
    private String businessDomain;

    /**
     * 环境.
     */
    @NotNull(message = "环境不能为空")
    @Schema(name = "env", description = "环境")
    private String env;

    /**
     * 服务类型.
     */
    @NotNull(message = "服务类型不能为空")
    @Schema(name = "serviceType", description = "服务类型")
    private String serviceType;

    /**
     * 服务英文名称.
     */
    @NotNull(message = "服务英文名称不能为空")
    @Schema(name = "serviceEnName", description = "服务英文名称")
    private String serviceEnName;

    /**
     * 请求方式.
     */
    @NotNull(message = "请求方式不能为空")
    @Schema(name = "requestType", description = "请求方式")
    private String requestType;

    /**
     * 接口地址url.
     */
    @NotNull(message = "接口地址url不能为空")
    @Schema(name = "requestUrl", description = "接口地址url，不需要拼接basePath")
    private String requestUrl;

    /**
     * 接口basePath.
     */
    //    @NotNull(message = "接口basePath不能为空")
    @Schema(name = "basePath", description = "basePath,如/api/openapi")
    private String basePath;

}
