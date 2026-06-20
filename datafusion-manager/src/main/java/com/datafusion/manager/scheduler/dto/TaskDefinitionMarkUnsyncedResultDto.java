package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 任务定义标记未同步响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskDefinitionMarkUnsyncedResultDto", description = "任务定义标记未同步响应Dto")
public class TaskDefinitionMarkUnsyncedResultDto {

    /**
     * 任务ID.
     */
    @Schema(name = "taskId", description = "任务ID")
    private UUID taskId;

    /**
     * 同步标识.
     */
    @Schema(name = "syncFlag", description = "同步标识")
    private Boolean syncFlag;
}
