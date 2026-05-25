package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * DAG保存Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "DagSaveDto", description = "DAG保存Dto")
public class DagSaveDto {

    /**
     * 流程ID.
     */
    @Schema(name = "flowId", description = "流程ID")
    @NotNull(message = "流程ID不能为空")
    private UUID flowId;

    /**
     * DAG节点列表(React Flow nodes).
     */
    @Schema(name = "nodes", description = "DAG节点列表")
    private List<NodeDto> nodes;

    /**
     * DAG连线列表(React Flow edges).
     */
    @Schema(name = "edges", description = "DAG连线列表")
    private List<EdgeDto> edges;
}
