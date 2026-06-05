package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统-插件配置查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Data
@Schema(name = "PluginConfigQueryDto", description = "插件配置查询条件Dto")
public class PluginConfigQueryDto {

    /**
     * 插件名称(模糊查询).
     */
    @Schema(name = "pluginName", description = "插件名称(模糊查询)")
    private String pluginName;

    /**
     * 插件类型.
     */
    @Schema(name = "pluginType", description = "插件类型")
    private String pluginType;

    /**
     * 运行模式.
     */
    @Schema(name = "runMode", description = "运行模式")
    private String runMode;

    /**
     * 是否模板数据.
     */
    @Schema(name = "isTemplate", description = "是否模板数据")
    private Boolean isTemplate;

    /**
     * 删除状态: 0-正常, 1-删除.
     */
    @Schema(name = "isDel", description = "删除状态: 0-正常, 1-删除")
    private Short isDel;
}
