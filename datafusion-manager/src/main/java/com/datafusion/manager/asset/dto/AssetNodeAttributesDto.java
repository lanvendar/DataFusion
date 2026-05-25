package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/3/17
 * @since 2026/3/17
 */
@Data
@Accessors(chain = true)
public class AssetNodeAttributesDto {
    /**
     * 当前节点的名称,表的时候,node为表名,subNodes,字段子节点,接口,指标.
     */
    @Schema(name = "attributeName", description = "当前节点的名称")
    private String attributeName;

    /**
     * 节点urn.
     */
    @Schema(name = "attributeUrn", description = "当前节点urn")
    private String attributeUrn;

}
