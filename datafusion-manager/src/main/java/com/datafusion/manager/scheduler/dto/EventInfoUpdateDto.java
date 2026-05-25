package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-事件修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/4/1
 * @since 1.0.0
 */
@Data
@Schema(name = "EventInfoUpdateDto", description = "事件修改Dto")
public class EventInfoUpdateDto {

    /**
     * 事件ID.
     */
    @Schema(name = "id", description = "事件ID")
    @NotNull(message = "事件ID不能为空")
    private UUID id;

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
     * 关联流程ID.
     */
    @Schema(name = "flowId", description = "关联流程ID")
    private UUID flowId;

    /**
     * 关联任务ID.
     */
    @Schema(name = "taskId", description = "关联任务ID")
    private UUID taskId;
}
