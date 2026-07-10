package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-流程开始调度请求Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/10
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowScheduleDto", description = "流程开始调度请求Dto")
public class FlowScheduleDto {

    /**
     * 流程ID.
     */
    @Schema(name = "id", description = "流程ID")
    @NotNull(message = "流程ID不能为空")
    private UUID id;

    /**
     * 触发器ID.
     */
    @Schema(name = "triggerId", description = "触发器ID")
    @NotNull(message = "触发器不能为空")
    private UUID triggerId;

    /**
     * 调度开始时间.
     */
    @Schema(name = "startTime", description = "调度开始时间")
    @NotNull(message = "调度开始时间不能为空")
    private Long startTime;

    /**
     * 调度结束时间.
     */
    @Schema(name = "endTime", description = "调度结束时间")
    @NotNull(message = "调度结束时间不能为空")
    private Long endTime;
}
