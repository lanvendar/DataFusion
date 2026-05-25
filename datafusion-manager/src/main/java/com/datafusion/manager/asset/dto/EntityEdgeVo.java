package com.datafusion.manager.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 实体边关系VO（节点到节点的边）.
 *
 * @author xufeng
 * @version 1.0.0, 2026/03/16
 * @since 2026/03/16
 */
@Data
public class EntityEdgeVo {

    /**
     * 边的唯一标识.
     */
    @Schema(name = "id", description = "边的唯一标识")
    private String id;

    /**
     * 源节点URN.
     */
    @Schema(name = "source", description = "源节点URN")
    private String source;

    /**
     * 目标节点URN.
     */
    @Schema(name = "target", description = "目标节点URN")
    private String target;

    /**
     * 属性值.
     */
    @Schema(name = "edgeProp", description = "目标节点URN")
    private JsonNode edgeProp;
}