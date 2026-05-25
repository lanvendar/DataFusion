package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调度-触发器查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/26
 * @since 1.0.0
 */
@Data
@Schema(name = "TriggerInfoQueryDto", description = "触发器查询条件Dto")
public class TriggerInfoQueryDto {

    /**
     * 触发器名称(模糊查询).
     */
    @Schema(name = "name", description = "触发器名称(模糊查询)")
    private String name;

    /**
     * 触发器类型过滤.
     */
    @Schema(name = "type", description = "触发器类型过滤: CRON / INTERVAL")
    private String type;

    /**
     * 调度策略过滤.
     */
    @Schema(name = "policy", description = "调度策略过滤")
    private String policy;
}
