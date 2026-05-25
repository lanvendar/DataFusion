package com.datafusion.manager.asset.dto.request;

import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 菜单资源导入请求体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/2/12
 * @since 2026/2/12
 */
@Data
public class MenuResourceReq {

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
     * 应用编码.
     */
    @NotNull(message = "应用编码不能为空")
    @Schema(name = "appCode", description = "应用编码")
    private String appCode;

    /**
     * 应用名称.
     */
    @NotNull(message = "应用名称不能为空")
    @Schema(name = "appName", description = "应用名称")
    private String appName;

    /**
     * 菜单ID.
     */
    @NotNull(message = "菜单ID不能为空")
    @Schema(name = "menuId", description = "菜单ID")
    private Long menuId;

    /**
     * 菜单名称.
     */
    @NotNull(message = "菜单名称不能为空")
    @Schema(name = "menuName", description = "菜单名称 格式：菜单1-菜单2-菜单3")
    private String menuName;

    /**
     * 菜单类型.
     */
    @NotNull(message = "菜单类型")
    @Schema(name = "componentType", description = "菜单类型")
    private Byte componentType;

    /**
     * 关联的API资源ID列表.
     */
    @NotNull(message = "API资源ID列表不能为空")
    @Schema(name = "apiResourceIds", description = "关联的API资源ID列表")
    private Map<UUID, Set<MetricsTagDto>> apiResourceIds;

}
