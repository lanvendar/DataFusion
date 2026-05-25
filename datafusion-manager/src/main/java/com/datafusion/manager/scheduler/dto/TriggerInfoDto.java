package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 调度-触发器响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/26
 * @since 1.0.0
 */
@Data
@Schema(name = "TriggerInfoDto", description = "触发器响应Dto")
public class TriggerInfoDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
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

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String creator;

    /**
     * 修改人.
     */
    @Schema(name = "updater", description = "修改人")
    private String updater;

    /**
     * 创建时间.
     */
    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    /**
     * 修改时间.
     */
    @Schema(name = "updateTime", description = "修改时间")
    private Date updateTime;
}
