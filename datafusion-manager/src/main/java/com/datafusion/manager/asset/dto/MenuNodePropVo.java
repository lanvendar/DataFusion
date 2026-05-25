package com.datafusion.manager.asset.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Menu节点属性.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/4/2
 * @since 2026/4/2
 */

@Data
@Accessors(chain = true)
public class MenuNodePropVo {
    /**
     * urn.
     */
    private String urn;

    /**
     * 应用code.
     */
    private String appCode;

    /**
     * 应用名称.
     */
    private String appName;

    /**
     * 菜单路径 菜单1-菜单2-菜单3.
     */
    private String menu;

    /**
     * 菜单类型.
     */
    private String componentType;
}
