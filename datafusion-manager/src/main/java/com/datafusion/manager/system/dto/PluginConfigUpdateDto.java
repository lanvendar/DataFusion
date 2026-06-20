package com.datafusion.manager.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 系统-插件配置修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Data
@Schema(name = "PluginConfigUpdateDto", description = "插件配置修改Dto")
public class PluginConfigUpdateDto {

    /**
     * 插件配置ID.
     */
    @Schema(name = "id", description = "插件配置ID")
    @NotNull(message = "插件配置ID不能为空")
    private UUID id;

    /**
     * 插件名称.
     */
    @Schema(name = "pluginName", description = "插件名称")
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
     * 描述.
     */
    @Schema(name = "description", description = "描述")
    private String description;

    /**
     * 插件配置.
     */
    @Schema(name = "pluginParam", description = "插件配置")
    private JsonNode pluginParam;

}
