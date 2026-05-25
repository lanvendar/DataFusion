package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/6
 * @since 2025/11/6
 */
@Data
public class LineEdgeNodeVo {
    
    /**
     * 深度,从查询节点,往上游,下游扩展,也有对齐血缘层级的作用.
     */
    @Schema(name = "depth", description = "深度")
    private Integer depth;
    
    /**
     * 节点信息,所有节点信息,目前均为二级结构,表字段.
     */
    private List<EdgeNodeVo> nodeVos;
    
    /**
     * 关系,节点之间的连线,根据sourceUrn去找targetUrn,一对多.
     */
    private Map<String, Set<String>> edgeVos;
    
}
