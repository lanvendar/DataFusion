package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Data
@Accessors(chain = true)
public class EdgeNodeVo {

    /**
     * 当前节点的名称,表的时候,node为表名,subNodes,字段子节点,接口,指标.
     */
    @Schema(name = "nodeName", description = "当前节点的名称")
    private String nodeName;

    /**
     * 节点urn.
     */
    @Schema(name = "nodeUrn", description = "当前节点urn")
    private String nodeUrn;

    /**
     * 节点类型.
     */
    @Schema(name = "nodeType", description = "节点类型")
    private String nodeType;

    /**
     * 节点子类.
     */
    @Schema(name = "nodeSubType", description = "当前节点urn")
    private String nodeSubType;

    /**
     * 字段级关系.
     */
    @Schema(name = "sourceUrn", description = "来源节点")
    private String sourceUrn;

    /**
     * 字段级关系.
     */
    @Schema(name = "targetUrn", description = "目标节点")
    private String targetUrn;

    /**
     * 子节点关系,如果是表和字段,则放字段联系.
     */
    @Schema(name = "子节点关系", description = "子节点")
    private Set<EdgeNodeVo> subEdges;

    /**
     * 测点信息.
     */
    @Schema(name = "tag", description = "测点信息")
    private String tag;

    /**
     * 维度信息.
     */
    @Schema(name = "dimension", description = "维度信息")
    private String dimension;

    /**
     * 子节点关系,如果是表和字段,则放字段联系.
     */
    @Schema(name = "attributes", description = "子节点")
    private Set<AssetNodeAttributesDto> attributes;

    /**
     * 子节点详情,用于存储冗余信息(如COLUMN列表).
     */
    @Data
    @Accessors(chain = true)
    public static class SubNodeDetail {
        /**
         * 子节点urn.
         */
        private String subNodeUrn;

        /**
         * 子节点名称.
         */
        private String subNodeName;

        /**
         * 子节点类型.
         */
        private String subNodeType;
    }

}
