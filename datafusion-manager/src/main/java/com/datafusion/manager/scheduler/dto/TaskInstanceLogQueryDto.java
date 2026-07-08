package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-任务实例日志查询Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInstanceLogQueryDto", description = "任务实例日志查询Dto")
public class TaskInstanceLogQueryDto {

    /**
     * 流程实例ID.
     */
    @Schema(name = "flowInstanceId", description = "流程实例ID")
    private UUID flowInstanceId;

    /**
     * 任务实例ID.
     */
    @Schema(name = "taskInstanceId", description = "任务实例ID")
    private UUID taskInstanceId;

    /**
     * 日志类型.
     */
    @Schema(name = "logType", description = "LOG、ERROR、STATUS、PLUGIN")
    private String logType;

    /**
     * 读取偏移.
     */
    @Schema(name = "offset", description = "读取偏移, 刷新时传0")
    private Long offset;

    /**
     * 读取大小.
     */
    @Schema(name = "limit", description = "读取大小")
    private Integer limit;
}
