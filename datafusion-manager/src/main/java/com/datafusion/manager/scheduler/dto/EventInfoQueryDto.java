package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-事件查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/4/1
 * @since 1.0.0
 */
@Data
@Schema(name = "EventInfoQueryDto", description = "事件查询条件Dto")
public class EventInfoQueryDto {

    /**
     * 事件名称(模糊查询).
     */
    @Schema(name = "eventName", description = "事件名称(模糊查询)")
    private String eventName;

    /**
     * 事件类型(精确过滤).
     */
    @Schema(name = "eventType", description = "事件类型(精确过滤)")
    private String eventType;

    /**
     * 关联流程ID(精确过滤).
     */
    @Schema(name = "flowId", description = "关联流程ID")
    private UUID flowId;

    /**
     * 关联任务ID(精确过滤).
     */
    @Schema(name = "taskId", description = "关联任务ID")
    private UUID taskId;
}
