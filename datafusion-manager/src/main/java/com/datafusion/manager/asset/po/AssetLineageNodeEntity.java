package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
@TableName("asset_lineage_node")
public class AssetLineageNodeEntity extends BaseIdEntity {
    
    /**
     * 节点urn.
     */
    @TableField("node_urn")
    private String nodeUrn;
    
    /**
     * 节点名称.
     */
    @TableField("node_name")
    private String nodeName;
    
    /**
     * 节点类型.
     */
    @TableField("node_type")
    private String nodeType;
    
    /**
     * 节点子类型.
     */
    @TableField("node_sub_type")
    private String nodeSubType;
    
    /**
     * 节点属性.
     */
    @TableField("node_prop")
    private JsonNode nodeProp;
    
}
