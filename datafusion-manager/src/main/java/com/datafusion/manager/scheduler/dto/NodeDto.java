package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * DAG节点Dto(React Flow Node).
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "NodeDto", description = "DAG节点Dto")
public class NodeDto {

    /**
     * 任务ID(节点ID, 对应task_info.id).
     */
    @Schema(name = "id", description = "任务ID(节点ID)")
    @NotBlank(message = "节点ID不能为空")
    private String id;

    /**
     * 业务数据(提交时忽略, 响应时从task_info填充).
     */
    @Schema(name = "data", description = "业务数据(响应时从task_info填充)")
    private NodeDataDto data;

    /**
     * 节点前端属性(React Flow props).
     */
    @Schema(name = "nodeView", description = "节点前端属性")
    private NodeViewDto nodeView;
}
