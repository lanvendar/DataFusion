package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 任务定义标记未同步Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskDefinitionMarkUnsyncedDto", description = "任务定义标记未同步Dto")
public class TaskDefinitionMarkUnsyncedDto {

    /**
     * 业务定位串.
     */
    @NotBlank(message = "bizRef不能为空")
    @Schema(name = "bizRef", description = "业务定位串")
    private String bizRef;

    /**
     * 原业务页面路由.
     */
    @Schema(name = "sourceRoute", description = "原业务页面路由")
    private String sourceRoute;

    /**
     * 标记原因.
     */
    @Schema(name = "reason", description = "标记原因")
    private String reason;
}
