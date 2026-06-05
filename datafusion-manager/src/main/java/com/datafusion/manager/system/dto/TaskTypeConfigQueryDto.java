package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 系统-任务类型配置查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskTypeConfigQueryDto", description = "任务类型配置查询条件Dto")
public class TaskTypeConfigQueryDto {

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 默认插件ID.
     */
    @Schema(name = "defaultPluginId", description = "默认插件ID")
    private UUID defaultPluginId;

    /**
     * 插件类型.
     */
    @Schema(name = "pluginType", description = "插件类型")
    private String pluginType;
}
