package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * DAG连线Dto(React Flow Edge).
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "EdgeViewDto", description = "DAG连线样式Dto")
public class EdgeViewDto {

    /**
     * 标签(前端生成).
     */
    @Schema(name = "label", description = "标签")
    private String label;

    /**
     * 边样式(前端透传, 持久化到task_link.view).
     */
    @Schema(name = "style", description = "边样式")
    private Map<String, Object> style;

    /**
     * 边附加样式(前端透传, 持久化到task_link.view).
     */
    @Schema(name = "extra", description = "边附加样式")
    private Map<String, Object> extra;


}
