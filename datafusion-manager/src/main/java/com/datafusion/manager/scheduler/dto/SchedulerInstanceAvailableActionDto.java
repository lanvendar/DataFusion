package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调度实例可用操作响应.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
@Schema(name = "SchedulerInstanceAvailableActionDto", description = "调度实例可用操作响应")
public class SchedulerInstanceAvailableActionDto {

    /**
     * 操作类型.
     */
    @Schema(name = "actionType", description = "操作类型")
    private String actionType;

    /**
     * 操作展示名称.
     */
    @Schema(name = "label", description = "操作展示名称")
    private String label;

    /**
     * 是否需要二次确认.
     */
    @Schema(name = "confirmRequired", description = "是否需要二次确认")
    private Boolean confirmRequired;
}
