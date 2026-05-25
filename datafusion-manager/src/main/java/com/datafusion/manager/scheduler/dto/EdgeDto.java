package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * DAG连线Dto(React Flow Edge).
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "EdgeDto", description = "DAG连线Dto")
public class EdgeDto {

    /**
     * 边ID(前端生成).
     */
    @Schema(name = "id", description = "边ID")
    private String id;

    /**
     * 上游任务ID.
     */
    @Schema(name = "source", description = "上游任务ID")
    @NotBlank(message = "上游任务ID不能为空")
    private String source;

    /**
     * 下游任务ID.
     */
    @Schema(name = "target", description = "下游任务ID")
    @NotBlank(message = "下游任务ID不能为空")
    private String target;

    /**
     * 边渲染类型(default, straight, step, smoothstep, 前端决定).
     */
    @Schema(name = "edgeView", description = "边渲染类型")
    private EdgeViewDto edgeView;
}
