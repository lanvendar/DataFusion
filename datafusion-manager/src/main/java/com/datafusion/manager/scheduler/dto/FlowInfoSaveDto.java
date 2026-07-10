package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * 调度-流程新增Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowInfoSaveDto", description = "流程新增Dto")
public class FlowInfoSaveDto {

    /**
     * 流程名称.
     */
    @Schema(name = "flowName", description = "流程名称")
    @NotBlank(message = "流程名称不能为空")
    private String flowName;

    /**
     * 流程编码(全局唯一).
     */
    @Schema(name = "flowCode", description = "流程编码(全局唯一)")
    @NotBlank(message = "流程编码不能为空")
    private String flowCode;

    /**
     * 流程分组.
     */
    @Schema(name = "groupId", description = "流程分组")
    private UUID groupId;

    /**
     * 流程描述.
     */
    @Schema(name = "description", description = "流程描述")
    private String description;

    /**
     * 流程类型.
     */
    @Schema(name = "flowType", description = "流程类型")
    @NotBlank(message = "流程类型不能为空")
    private String flowType;

    /**
     * 流程变量参数(JSON).
     */
    @Schema(name = "flowParam", description = "流程变量参数(JSON)")
    private String flowParam;

    /**
     * 依赖事件ID列表.
     */
    @Schema(name = "depEventIds", description = "依赖事件ID列表")
    private List<String> depEventIds;

}
