package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程DAG响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowDagDto", description = "流程DAG响应Dto")
public class FlowDagDto {

    /**
     * 流程ID.
     */
    @Schema(name = "flowId", description = "流程ID")
    private UUID flowId;

    /**
     * DAG节点列表(含data业务信息).
     */
    @Schema(name = "nodes", description = "DAG节点列表")
    private List<NodeDto> nodes;

    /**
     * DAG连线列表.
     */
    @Schema(name = "edges", description = "DAG连线列表")
    private List<EdgeDto> edges;
}
