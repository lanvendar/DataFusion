package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-任务查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInfoQueryDto", description = "任务查询条件Dto")
public class TaskInfoQueryDto {

    /**
     * 任务名称(模糊查询).
     */
    @Schema(name = "taskName", description = "任务名称(模糊查询)")
    private String taskName;

    /**
     * 任务编码(模糊查询).
     */
    @Schema(name = "taskCode", description = "任务编码(模糊查询)")
    private String taskCode;

    /**
     * 任务类型过滤.
     */
    @Schema(name = "taskType", description = "任务类型过滤")
    private String taskType;

    /**
     * 所属流程ID过滤.
     */
    @Schema(name = "flowId", description = "所属流程ID过滤")
    private UUID flowId;

    /**
     * 启用状态过滤.
     */
    @Schema(name = "enabled", description = "启用状态过滤")
    private Boolean enabled;

    /**
     * 绑定状态过滤.
     */
    @Schema(name = "isBound", description = "绑定状态过滤")
    private Boolean isBound;

    /**
     * 同步状态过滤.
     */
    @Schema(name = "syncFlag", description = "同步状态过滤")
    private Boolean syncFlag;
}
