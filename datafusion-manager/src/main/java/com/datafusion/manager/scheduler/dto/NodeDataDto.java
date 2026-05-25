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
     * 任务类型(响应时从task_info填充).
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;
}
