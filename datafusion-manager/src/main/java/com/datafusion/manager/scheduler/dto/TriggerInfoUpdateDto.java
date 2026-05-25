package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-触发器修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/26
 * @since 1.0.0
 */
@Data
@Schema(name = "TriggerInfoUpdateDto", description = "触发器修改Dto")
public class TriggerInfoUpdateDto {

    /**
     * 触发器ID.
     */
    @Schema(name = "id", description = "触发器ID")
    @NotNull(message = "触发器ID不能为空")
    private UUID id;

    /**
     * 触发器名称.
     */
    @Schema(name = "name", description = "触发器名称")
    private String name;

    /**
     * 触发器类型: CRON / INTERVAL.
     */
    @Schema(name = "type", description = "触发器类型: CRON / INTERVAL")
    private String type;

    /**
     * 调度策略.
     */
    @Schema(name = "policy", description = "调度策略")
    private String policy;

    /**
     * cron表达式.
     */
    @Schema(name = "cron", description = "cron表达式")
    private String cron;

    /**
     * 周期间隔, 单位分钟.
     */
    @Schema(name = "interval", description = "周期间隔, 单位分钟")
    private Integer interval;
}
