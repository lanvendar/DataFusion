package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调度-流程查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowInfoQueryDto", description = "流程查询条件Dto")
public class FlowInfoQueryDto {

    /**
     * 流程名称(模糊查询).
     */
    @Schema(name = "flowName", description = "流程名称(模糊查询)")
    private String flowName;

    /**
     * 流程类型过滤.
     */
    @Schema(name = "flowType", description = "流程类型过滤")
    private String flowType;

    /**
     * 调度状态过滤.
     */
    @Schema(name = "enabled", description = "调度状态过滤")
    private Boolean enabled;

    /**
     * 发布状态过滤.
     */
    @Schema(name = "publishState", description = "发布状态过滤")
    private Boolean publishState;
}
