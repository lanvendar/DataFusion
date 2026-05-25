package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * DAG节点Dto(React Flow Node).
 *
 * @author lanvendar
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "NodeViewDto", description = "DAG节点样式Dto")
public class NodeViewDto {
    /**
     * 节点画布坐标(React Flow position).
     */
    @Schema(name = "position", description = "节点画布坐标")
    private PositionDto position;

    /**
     * 节点样式(default, custom等, 前端决定).
     */
    @Schema(name = "style", description = "节点样式")
    private Map<String, Object> style;

    /**
     * 节点额外样式(提交时忽略, 响应时填充).
     */
    @Schema(name = "extra", description = "节点额外样式")
    private Map<String, Object> extra;


}
