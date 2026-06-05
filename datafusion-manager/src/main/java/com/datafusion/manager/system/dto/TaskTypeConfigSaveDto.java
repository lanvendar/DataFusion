package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 系统-任务类型配置新增Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskTypeConfigSaveDto", description = "任务类型配置新增Dto")
public class TaskTypeConfigSaveDto {

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    @NotBlank(message = "任务类型不能为空")
    private String taskType;

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
