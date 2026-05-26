package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 节点资源关系实体.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("asset_lineage_node_resource_relation")
public class AssetLineageNodeResourceRelationEntity extends BaseIdEntity {
    
    /**
     * 资源名称.
     */
    @TableField("resource_id")
    private UUID resourceId;
    
    /**
     * 节点名称.
     */
    @TableField("node_id")
    private UUID nodeId;
    
}
