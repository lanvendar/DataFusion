package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * app响应体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/2/13
 * @since 2026/2/13
 */
@Data
public class MenuResp {
    /**
     * 菜单ID.
     */
    @Schema(name = "menuId", description = "菜单ID")
    private Long menuId;

    /**
     * componentUrl.
     */
    @Schema(name = "componentUrl", description = "组件url")
    private String componentUrl;

    /**
     * componentType.
     */
    @Schema(name = "componentType", description = "组件类型")
    private Byte componentType;

    /**
     * 应用名称.
     */
    @Schema(name = "menuName", description = "菜单名称")
    private String menuName;

    /**
     * 子菜单.
     */
    @Schema(name = "children", description = "子菜单列表")
    private List<MenuResp> children;

}
