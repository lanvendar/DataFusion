package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 系统-任务类型配置修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskTypeConfigUpdateDto", description = "任务类型配置修改Dto")
public class TaskTypeConfigUpdateDto {

    /**
     * 任务类型配置ID.
     */
    @Schema(name = "id", description = "任务类型配置ID")
    @NotNull(message = "任务类型配置ID不能为空")
    private UUID id;

    /**
     * 默认插件ID.
     */
    @Schema(name = "defaultPluginId", description = "默认插件ID")
    @NotNull(message = "默认插件ID不能为空")
    private UUID defaultPluginId;

    /**
     * 插件类型.
     */
    @Schema(name = "pluginType", description = "插件类型")
    private String pluginType;
}
