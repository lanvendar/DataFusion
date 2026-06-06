package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 调度-流程修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowInfoUpdateDto", description = "流程修改Dto")
public class FlowInfoUpdateDto {

    /**
     * 流程ID.
     */
    @Schema(name = "id", description = "流程ID")
    @NotNull(message = "流程ID不能为空")
    private UUID id;

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
     * 流程分组.
     */
    @Schema(name = "groupId", description = "流程分组")
    private UUID groupId;

    /**
     * 流程描述.
     */
    @Schema(name = "description", description = "流程描述")
    private String description;

    /**
     * 流程类型.
     */
    @Schema(name = "flowType", description = "流程类型")
    private String flowType;

    /**
     * 流程变量参数(JSON).
     */
    @Schema(name = "flowParam", description = "流程变量参数(JSON)")
    private String flowParam;

    /**
     * 调度开始时间.
     */
    @Schema(name = "startTime", description = "调度开始时间")
    private Long startTime;

    /**
     * 调度结束时间.
     */
    @Schema(name = "endTime", description = "调度结束时间")
    private Long endTime;

    /**
     * 依赖事件ID列表.
     */
    @Schema(name = "depEventIds", description = "依赖事件ID列表")
    private List<String> depEventIds;

    /**
     * 触发器ID.
     */
    @Schema(name = "triggerId", description = "触发器ID")
    private UUID triggerId;
}
