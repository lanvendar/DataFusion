package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DAG节点业务数据Dto(React Flow node.data).
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "NodeDataDto", description = "节点业务数据Dto")
public class NodeDataDto {
    /**
     * 任务Id(响应时从task_info填充).
     */
    @Schema(name = "taskId", description = "任务Id")
    private String taskId;

    /**
     * 任务名称(响应时从task_info填充).
     */
    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    /**
     * 任务编码(响应时从task_info填充).
     */
    @Schema(name = "taskCode", description = "任务编码")
    private String taskCode;

    /**
     * 任务类型(响应时从task_info填充).
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 任务描述(响应时从task_info填充).
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 任务同步标识(响应时从task_info填充).
     */
    @Schema(name = "syncFlag", description = "任务同步标识")
    private Boolean syncFlag;

    /**
     * 执行组件ID(响应时从task_info填充).
     */
    @Schema(name = "pluginId", description = "执行组件ID")
    private String pluginId;

    /**
     * 依赖事件ID(响应时从task_info填充).
     */
    @Schema(name = "depEventIds", description = "依赖事件ID")
    private String depEventIds;

    /**
     * 任务产出事件ID(响应时从task_info填充).
     */
    @Schema(name = "eventId", description = "任务产出事件ID")
    private String eventId;

    /**
     * 是否启用(响应时从task_info填充).
     */
    @Schema(name = "enabled", description = "是否启用")
    private Boolean enabled;

    /**
     * 任务变量参数JSON(响应时从task_info填充).
     */
    @Schema(name = "taskParam", description = "任务变量参数JSON")
    private String taskParam;

    /**
     * 任务定义JSON(响应时从task_info填充).
     */
    @Schema(name = "definition", description = "任务定义JSON")
    private String definition;
}
