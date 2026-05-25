package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Data
public class MenuSnapshot {
    
    /**
     * 组织名称.
     */
    @Schema(name = "organization", description = "组织名称")
    private String organization = "goodwe";
    
    /**
     * 业务域.
     */
    @Schema(name = "businessDomain", description = "业务域")
    private String businessDomain;
    
    /**
     * 模块名称.
     */
    @Schema(name = "moduleName", description = "模块名称")
    private String moduleName;
    
    /**
     * 菜单url.
     */
    @NotNull(message = "菜单url")
    @Schema(name = "componentUrl", description = "菜单子节点url")
    private String componentUrl;
    
    /**
     * 菜单名全路径.
     */
    @NotNull(message = "菜单名全路径")
    @Schema(name = "fullNamePath", description = "菜单名全路径")
    private String fullNamePath;
    
    /**
     * 接口id.
     */
    @NotNull(message = "菜单对应的api")
    @Schema(name = "apiIds", description = "菜单对应的api")
    private List<UUID> apiIds;
    
}
