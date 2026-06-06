package com.datafusion.manager.scheduler.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务实例响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInstanceDto", description = "任务实例响应Dto")
public class TaskInstanceDto {

    /**
     * 任务实例ID.
     */
    @Schema(name = "id", description = "任务实例ID")
    private UUID id;

    /**
     * 流程实例ID.
     */
    @Schema(name = "flowInstanceId", description = "流程实例ID")
    private UUID flowInstanceId;

    /**
     * 任务ID.
     */
    @Schema(name = "taskId", description = "任务ID")
    private UUID taskId;

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

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
     * 任务状态.
     */
    @Schema(name = "status", description = "任务状态")
    private String status;

    /**
     * 开始时间.
     */
    @Schema(name = "startTime", description = "开始时间")
    private Long startTime;

    /**
     * 结束时间.
     */
    @Schema(name = "endTime", description = "结束时间")
    private Long endTime;

    /**
     * 耗时.
     */
    @Schema(name = "costTime", description = "耗时")
    private Integer costTime;

    /**
     * 上游任务实例ID.
     */
    @Schema(name = "lastInstanceId", description = "上游任务实例ID")
    private String lastInstanceId;

    /**
     * 下游任务实例ID.
     */
    @Schema(name = "nextInstanceId", description = "下游任务实例ID")
    private String nextInstanceId;

    /**
     * worker ID.
     */
    @Schema(name = "workerId", description = "worker ID")
    private UUID workerId;

    /**
     * worker 返回结果.
     */
    @Schema(name = "workerResult", description = "worker 返回结果")
    private JsonNode workerResult;

    /**
     * worker 返回摘要.
     */
    @Schema(name = "workerResultText", description = "worker 返回摘要")
    private String workerResultText;

    /**
     * 日志路径.
     */
    @Schema(name = "logPath", description = "日志路径")
    private String logPath;

    /**
     * 可用操作.
     */
    @Schema(name = "availableActions", description = "可用操作")
    private List<SchedulerInstanceAvailableActionDto> availableActions;
}
