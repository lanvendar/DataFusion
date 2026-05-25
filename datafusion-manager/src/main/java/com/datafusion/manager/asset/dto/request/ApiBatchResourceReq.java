package com.datafusion.manager.asset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * api资源批量导入实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class ApiBatchResourceReq {
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

}
