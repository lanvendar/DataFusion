package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 调度-cron 预览结果Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/25
 * @since 1.0.0
 */
@Data
@Schema(name = "TriggerCronPreviewResultDto", description = "cron 预览结果Dto")
public class TriggerCronPreviewResultDto {

    /**
     * cron表达式.
     */
    @Schema(name = "cron", description = "cron表达式")
    private String cron;

    /**
     * 服务端时区.
     */
    @Schema(name = "timeZone", description = "服务端时区")
    private String timeZone;

    /**
     * 后续运行时间戳.
     */
    @Schema(name = "nextTimes", description = "后续运行时间戳")
    private List<Long> nextTimes;
}
