package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 调度-触发器新增Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/26
 * @since 1.0.0
 */
@Data
@Schema(name = "TriggerInfoSaveDto", description = "触发器新增Dto")
public class TriggerInfoSaveDto {

    /**
     * 触发器名称.
     */
    @Schema(name = "name", description = "触发器名称")
    @NotBlank(message = "触发器名称不能为空")
    private String name;

    /**
     * 触发器类型: CRON / INTERVAL.
     */
    @Schema(name = "type", description = "触发器类型: CRON / INTERVAL")
    @NotBlank(message = "触发器类型不能为空")
    private String type;

    /**
     * 调度策略: EXECUTE_ONCE / SERIAL_WAIT / PARALLEL / DISCARD_NEW / DISCARD_OLD.
     */
    @Schema(name = "policy", description = "调度策略: EXECUTE_ONCE / SERIAL_WAIT / PARALLEL / DISCARD_NEW / DISCARD_OLD")
    @NotBlank(message = "调度策略不能为空")
    private String policy;

    /**
     * cron表达式(type=CRON时必填).
     */
    @Schema(name = "cron", description = "cron表达式(type=CRON时必填)")
    private String cron;

    /**
     * 周期间隔, 单位分钟(type=INTERVAL时必填).
     */
    @Schema(name = "interval", description = "周期间隔, 单位分钟(type=INTERVAL时必填)")
    private Integer interval;
}
