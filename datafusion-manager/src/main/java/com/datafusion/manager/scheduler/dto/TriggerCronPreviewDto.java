package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 调度-cron 预览请求Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/25
 * @since 1.0.0
 */
@Data
@Schema(name = "TriggerCronPreviewDto", description = "cron 预览请求Dto")
public class TriggerCronPreviewDto {

    /**
     * cron表达式.
     */
    @Schema(name = "cron", description = "cron表达式")
    @NotBlank(message = "cron表达式不能为空")
    private String cron;

    /**
     * 预览数量.
     */
    @Schema(name = "count", description = "预览数量")
    private Integer count;
}
