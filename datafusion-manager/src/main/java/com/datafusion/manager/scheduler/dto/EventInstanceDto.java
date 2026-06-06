package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-事件实例响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "EventInstanceDto", description = "事件实例响应Dto")
public class EventInstanceDto {

    /**
     * 事件实例ID.
     */
    @Schema(name = "id", description = "事件实例ID")
    private UUID id;

    /**
     * 事件ID.
     */
    @Schema(name = "eventId", description = "事件ID")
    private UUID eventId;

    /**
     * 事件名称.
     */
    @Schema(name = "eventName", description = "事件名称")
    private String eventName;

    /**
     * 事件类型.
     */
    @Schema(name = "eventType", description = "事件类型")
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
     * 事件生效时间.
     */
    @Schema(name = "effectTime", description = "事件生效时间")
    private Long effectTime;

    /**
     * 事件开始生效时间.
     */
    @Schema(name = "effectBeginTime", description = "事件开始生效时间")
    private Long effectBeginTime;

    /**
     * 事件结束生效时间.
     */
    @Schema(name = "effectEndTime", description = "事件结束生效时间")
    private Long effectEndTime;
}
