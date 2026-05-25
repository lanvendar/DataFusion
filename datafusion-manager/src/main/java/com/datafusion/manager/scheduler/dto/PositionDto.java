package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DAG节点坐标Dto(React Flow node.position).
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "PositionDto", description = "坐标Dto")
public class PositionDto {

    /**
     * 画布X坐标.
     */
    @Schema(name = "x", description = "画布X坐标")
    private Double x;

    /**
     * 画布Y坐标.
     */
    @Schema(name = "y", description = "画布Y坐标")
    private Double y;
}
