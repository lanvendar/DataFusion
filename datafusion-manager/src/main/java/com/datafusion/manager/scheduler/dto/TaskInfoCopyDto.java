package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-任务复制Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/29
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInfoCopyDto", description = "任务复制Dto")
public class TaskInfoCopyDto {

    /**
     * 被复制的原任务ID.
     */
    @Schema(name = "sourceId", description = "被复制的原任务ID")
    @NotNull(message = "原任务ID不能为空")
    private UUID sourceId;
}
