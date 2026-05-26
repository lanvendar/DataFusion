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
 * ETL 过程记录表实体类.
 * 用于记录 ETL 解析过程数据，供后续分析使用
 *
 * @author xufeng
 * @version 1.0.0, 2026/2/28
 * @since 2026/2/28
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("asset_etl_process")
public class AssetEtlProcessEntity extends BaseIdEntity {

    /**
     * 资源名称.
     */
    @TableField("resource_name")
    private String resourceName;

    /**
     * 资源状态.
     */
    @TableField("status")
    private Integer status;

    /**
     * 资源快照 JSON.
     */
    @TableField("resource_snapshot")
    private JsonNode resourceSnapshot;

    /**
     * 解析引擎类型: 1=calcite, 2=druid.
     */
    @TableField("engine")
    private Integer engine;

    /**
     * 解析备注信息，记录 Druid 解析过程中的 SUCCESS/FAILURE 日志.
     */
    @TableField("remark")
    private String remark;
}
