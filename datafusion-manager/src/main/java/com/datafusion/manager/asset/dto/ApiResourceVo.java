package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/20
 * @since 2025/11/20
 */
@Data
public class ApiResourceVo {
    
    /**
     * api资源id.
     */
    @Schema(name = "apiResourceId", description = "api资源id")
    private UUID apiResourceId;
    
    /**
     * api 服务名称.
     */
    @Schema(name = "serviceName", description = "服务名称")
    private String serviceName;
    
    /**
     * api url.
     */
    @Schema(name = "url", description = "url")
    private String apiUrl;
    
    /**
     * api method.
     */
    @Schema(name = "method", description = "方法")
    private String method;
}
