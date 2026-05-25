package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * api 资源查询接口实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/20
 * @since 2025/10/20
 */
@Data
public class ApiResourceQueryDto {
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
    @Schema(name = "requestUrl", description = "接口地址url")
    private String requestUrl;

}
