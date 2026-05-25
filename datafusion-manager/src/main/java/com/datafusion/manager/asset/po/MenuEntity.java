package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 菜单临时表实体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2025/11/13
 * @since 2025/11/13
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("asset_lineage_menu_tmp")
public class MenuEntity {

    /**
     * 菜单ID (来自permission_info.id).
     */
    @TableId("id")
    @TableField("id")
    private Long id;

    /**
     * 菜单名称 (来自permission_info.name).
     */
    @TableField("name")
    private String name;

    /**
     * 父菜单ID.
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 菜单URL.
     */
    @TableField("component_url")
    private String componentUrl;

    /**
     * 菜单类型.
     */
    @TableField("component_type")
    private Byte componentType;

    /**
     * 应用ID (来自application_info.id).
     */
    @TableField("app_id")
    private Long appId;

    /**
     * 应用编码 (来自application_info.code).
     */
    @TableField("app_code")
    private String appCode;

    /**
     * 应用名称 (来自application_info.name).
     */
    @TableField("app_name")
    private String appName;

}
