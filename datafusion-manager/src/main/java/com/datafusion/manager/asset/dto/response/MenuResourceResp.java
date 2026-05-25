package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 菜单资源响应体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/2/12
 * @since 2026/2/12
 */
@Data
public class MenuResourceResp {

    /**
     * 资源ID.
     */
    @Schema(name = "resourceId", description = "资源ID")
    private UUID resourceId;

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
     * 应用编码.
     */
    @Schema(name = "appCode", description = "应用编码")
    private String appCode;

    /**
     * 应用名称.
     */
    @Schema(name = "appName", description = "应用名称")
    private String appName;

    /**
     * 菜单ID.
     */
    @Schema(name = "menuId", description = "菜单ID")
    private Long menuId;

    /**
     * 菜单名称.
     */
    @Schema(name = "menuName", description = "菜单名称")
    private String menuName;

    /**
     * 关联的API资源ID列表.
     */
    @Schema(name = "apiResources", description = "关联的API资源列表")
    private List<ApiResourceNameResp> apiResources;

}
