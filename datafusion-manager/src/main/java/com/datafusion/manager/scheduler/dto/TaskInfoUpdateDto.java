package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-任务修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInfoUpdateDto", description = "任务修改Dto")
public class TaskInfoUpdateDto {

    /**
     * 任务ID.
     */
    @Schema(name = "id", description = "任务ID")
    @NotNull(message = "任务ID不能为空")
    private UUID id;

    /**
     * 任务名称.
     */
    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    /**
     * 任务编码.
     */
    @Schema(name = "taskCode", description = "任务编码")
    private String taskCode;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 任务类型ID.
     */
    @Schema(name = "taskTypeId", description = "任务类型ID")
    private String taskTypeId;

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 任务参数(JSON).
     */
    @Schema(name = "taskParam", description = "任务参数(JSON)")
    private String taskParam;

    /**
     * 任务定义(JSON).
     */
    @Schema(name = "definition", description = "任务定义(JSON)")
    private String definition;

    /**
     * 执行组件ID.
     */
    @Schema(name = "pluginId", description = "执行组件ID")
    private UUID pluginId;

    /**
     * 任务前端视图(JSON).
     */
    @Schema(name = "view", description = "任务前端视图(JSON)")
    private String view;

    /**
     * 依赖事件ID.
     */
    @Schema(name = "depEventIds", description = "依赖事件ID")
    private String depEventIds;

    /**
     * 事件ID.
     */
    @Schema(name = "eventId", description = "事件ID")
    private UUID eventId;

    /**
     * 是否启用.
     */
    @Schema(name = "enabled", description = "是否启用")
    private Boolean enabled;
}
