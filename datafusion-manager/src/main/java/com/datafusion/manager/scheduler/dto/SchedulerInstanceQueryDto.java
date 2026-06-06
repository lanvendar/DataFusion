package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调度-实例查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "SchedulerInstanceQueryDto", description = "调度实例查询条件Dto")
public class SchedulerInstanceQueryDto {

    /**
     * 流程名称或流程实例ID.
     */
    @Schema(name = "flowKeyword", description = "流程名称或流程实例ID")
    private String flowKeyword;

    /**
     * 任务名称或任务实例ID.
     */
    @Schema(name = "taskKeyword", description = "任务名称或任务实例ID")
    private String taskKeyword;

    /**
     * 实例状态.
     */
    @Schema(name = "status", description = "实例状态，使用StatusEnum.stateType")
    private String status;

    /**
     * 查询视图类型.
     */
    @Schema(name = "viewType", description = "REALTIME查询实时表，HISTORY查询历史表")
    private String viewType;

    /**
     * 调度开始时间.
     */
    @Schema(name = "scheduleStartTime", description = "调度开始时间")
    private Long scheduleStartTime;

    /**
     * 调度结束时间.
     */
    @Schema(name = "scheduleEndTime", description = "调度结束时间")
    private Long scheduleEndTime;

    /**
     * 开始时间下限.
     */
    @Schema(name = "startTime", description = "开始时间下限")
    private Long startTime;

    /**
     * 开始时间上限.
     */
    @Schema(name = "endTime", description = "开始时间上限")
    private Long endTime;

    /**
     * 结束时间下限.
     */
    @Schema(name = "finishStartTime", description = "结束时间下限")
    private Long finishStartTime;

    /**
     * 结束时间上限.
     */
    @Schema(name = "finishEndTime", description = "结束时间上限")
    private Long finishEndTime;
}
