package com.datafusion.manager.scheduler.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程实例响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowInstanceDto", description = "流程实例响应Dto")
public class FlowInstanceDto {

    /**
     * 流程实例ID.
     */
    @Schema(name = "id", description = "流程实例ID")
    private UUID id;

    /**
     * 流程ID.
     */
    @Schema(name = "flowId", description = "流程ID")
    private UUID flowId;

    /**
     * 流程名称.
     */
    @Schema(name = "flowName", description = "流程名称")
    private String flowName;

    /**
     * 流程编码.
     */
    @Schema(name = "flowCode", description = "流程编码")
    private String flowCode;

    /**
     * 流程类型.
     */
    @Schema(name = "flowType", description = "流程类型")
    private String flowType;

    /**
     * 实例状态.
     */
    @Schema(name = "status", description = "实例状态")
    private String status;

    /**
     * 触发器ID.
     */
    @Schema(name = "triggerId", description = "触发器ID")
    private String triggerId;

    /**
     * 发布版本.
     */
    @Schema(name = "publishVersion", description = "发布版本")
    private Long publishVersion;

    /**
     * 调度时间.
     */
    @Schema(name = "scheduleTime", description = "调度时间")
    private Long scheduleTime;

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
    @Schema(name = "duration", description = "耗时")
    private Long duration;

    /**
     * 流程DAG快照.
     */
    @Schema(name = "flowDagSnapshot", description = "流程DAG快照")
    private JsonNode flowDagSnapshot;

    /**
     * 可用操作.
     */
    @Schema(name = "availableActions", description = "可用操作")
    private List<SchedulerInstanceAvailableActionDto> availableActions;
}
