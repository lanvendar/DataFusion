package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 任务定义统一登记响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskDefinitionRegisterResultDto", description = "任务定义统一登记响应Dto")
public class TaskDefinitionRegisterResultDto {

    /**
     * 任务ID.
     */
    @Schema(name = "taskId", description = "任务ID")
    private UUID taskId;

    /**
     * 任务编码.
     */
    @Schema(name = "taskCode", description = "任务编码")
    private String taskCode;

    /**
     * 是否新建.
     */
    @Schema(name = "created", description = "是否新建")
    private Boolean created;

    /**
     * 同步标识.
     */
    @Schema(name = "syncFlag", description = "同步标识")
    private Boolean syncFlag;

    /**
     * 是否已绑定流程.
     */
    @Schema(name = "isBound", description = "是否已绑定流程")
    private Boolean isBound;

    /**
     * 流程ID.
     */
    @Schema(name = "flowId", description = "流程ID")
    private UUID flowId;
}
