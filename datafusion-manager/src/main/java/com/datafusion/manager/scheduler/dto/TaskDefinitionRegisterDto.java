package com.datafusion.manager.scheduler.dto;

import com.datafusion.manager.scheduler.model.BusinessSourceRoute;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 任务定义统一登记Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskDefinitionRegisterDto", description = "任务定义统一登记Dto")
public class TaskDefinitionRegisterDto {

    /**
     * 任务名称.
     */
    @NotBlank(message = "任务名称不能为空")
    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    /**
     * 任务编码.
     */
    @Schema(name = "taskCode", description = "任务编码")
    private String taskCode;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 任务类型ID.
     */
    @NotBlank(message = "任务类型ID不能为空")
    @Schema(name = "taskTypeId", description = "任务类型ID")
    private String taskTypeId;

    /**
     * 任务类型.
     */
    @NotBlank(message = "任务类型不能为空")
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 任务变量参数.
     */
    @Schema(name = "taskParam", description = "任务变量参数")
    private JsonNode taskParam;

    /**
     * 任务定义.
     */
    @NotNull(message = "任务定义不能为空")
    @Schema(name = "definition", description = "任务定义")
    private JsonNode definition;

    /**
     * 业务来源定位信息.
     */
    @Valid
    @NotNull(message = "业务来源定位信息不能为空")
    @Schema(name = "sourceRoute", description = "业务来源定位信息")
    private BusinessSourceRoute sourceRoute;
}
