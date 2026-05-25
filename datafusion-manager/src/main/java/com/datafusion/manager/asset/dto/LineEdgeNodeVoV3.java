package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/3/17
 * @since 2026/3/17
 */
@Data
public class LineEdgeNodeVoV3 {

    /**
     * 深度,从查询节点,往上游,下游扩展,也有对齐血缘层级的作用.
     */
    @Schema(name = "depth", description = "深度")
    private Integer depth;

    /**
     * 节点信息,所有节点信息,目前均为二级结构,表字段.
     */
    @Schema(name = "nodeVos", description = "节点信息")
    private List<EdgeNodeVoV2> nodeVos;

    /**
     * 实体边关系（节点到节点的边）.
     */
    @Schema(name = "entityEdgeVos", description = "实体边关系（节点到节点）")
    private List<EntityEdgeVo> entityEdgeVos;

    /**
     * 属性边关系（字段到字段的边，包含连接点信息）.
     */
    @Schema(name = "attributeEdgeVos", description = "属性边关系（字段到字段）")
    private List<AttributeEdgeVo> attributeEdgeVos;

}