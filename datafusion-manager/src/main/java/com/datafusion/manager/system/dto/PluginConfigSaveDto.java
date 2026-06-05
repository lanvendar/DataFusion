package com.datafusion.manager.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 系统-插件配置新增Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Data
@Schema(name = "PluginConfigSaveDto", description = "插件配置新增Dto")
public class PluginConfigSaveDto {

    /**
     * 插件名称.
     */
    @Schema(name = "pluginName", description = "插件名称")
    @NotBlank(message = "插件名称不能为空")
    private String pluginName;

    /**
     * 插件类型.
     */
    @Schema(name = "pluginType", description = "插件类型")
    @NotBlank(message = "插件类型不能为空")
    private String pluginType;

    /**
     * 运行模式.
     */
    @Schema(name = "runMode", description = "运行模式")
    @NotBlank(message = "运行模式不能为空")
    private String runMode;

    /**
     * 描述.
     */
    @Schema(name = "description", description = "描述")
    private String description;

    /**
     * 插件配置.
     */
    @Schema(name = "env", description = "插件配置")
    private JsonNode env;

}
