package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("asset_lineage_edge")
public class AssetLineageEdgeEntity extends BaseIdEntity {
    
    /**
     * 血缘关系起点urn.
     */
    @TableField("source_urn")
    private String sourceUrn;
    
    /**
     * 血缘关系终点urn.
     */
    @TableField("target_urn")
    private String targetUrn;
    
    /**
     * 资源id.
     */
    @TableField("resource_id")
    private UUID resourceId;

    /**
     * 属性.
     */
    @TableField("edge_prop")
    private JsonNode edgeProp;


    
}
