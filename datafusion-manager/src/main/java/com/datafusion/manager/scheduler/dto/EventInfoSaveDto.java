package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * 调度-事件新增Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/4/1
 * @since 1.0.0
 */
@Data
@Schema(name = "EventInfoSaveDto", description = "事件新增Dto")
public class EventInfoSaveDto {

    /**
     * 事件名称.
     */
    @Schema(name = "eventName", description = "事件名称")
    @NotBlank(message = "事件名称不能为空")
    private String eventName;

    /**
     * 事件类型("1"=TASK, "2"=FLOW).
     */
    @Schema(name = "eventType", description = "事件类型(1=TASK, 2=FLOW)")
    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    /**
     * 关联流程ID(eventType=FLOW时必传).
     */
    @Schema(name = "flowId", description = "关联流程ID")
    private UUID flowId;

    /**
     * 关联任务ID(eventType=TASK时必传).
     */
    @Schema(name = "taskId", description = "关联任务ID")
    private UUID taskId;
}
