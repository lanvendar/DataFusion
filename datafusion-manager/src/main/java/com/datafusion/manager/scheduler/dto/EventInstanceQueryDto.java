package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-事件实例查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "EventInstanceQueryDto", description = "事件实例查询条件Dto")
public class EventInstanceQueryDto {

    /**
     * 事件名称或事件ID.
     */
    @Schema(name = "eventKeyword", description = "事件名称或事件ID")
    private String eventKeyword;

    /**
     * 事件类型.
     */
    @Schema(name = "eventType", description = "事件类型，实例表存储值为1或2")
    private String eventType;

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
     * 事件生效开始时间.
     */
    @Schema(name = "effectStartTime", description = "事件生效开始时间")
    private Long effectStartTime;

    /**
     * 事件生效结束时间.
     */
    @Schema(name = "effectEndTime", description = "事件生效结束时间")
    private Long effectEndTime;
}
