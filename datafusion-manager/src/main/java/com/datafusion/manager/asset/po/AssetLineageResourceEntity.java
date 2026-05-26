package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("asset_lineage_resource")
public class AssetLineageResourceEntity extends BaseIdEntity {

    /**
     * 资源名称.
     */
    @TableField("resource_name")
    private String resourceName;

    /**
     * 资源标签.
     */
    @TableField("resource_tag")
    private Integer resourceTag;

    /**
     * 状态:0导入完成,1录入血缘中,2录入完成,3录入失败,.
     */
    @TableField("status")
    private Integer status = 0;

    /**
     * 结果.
     */
    @TableField("result")
    private JsonNode result;

    /**
     * 资源类型.
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 资源快照.
     */
    @TableField("resource_snapshot")
    private JsonNode resourceSnapshot;

    /**
     * 解析结果快照.
     */
    @TableField("result_snapshot")
    private JsonNode resultSnapshot;

}
