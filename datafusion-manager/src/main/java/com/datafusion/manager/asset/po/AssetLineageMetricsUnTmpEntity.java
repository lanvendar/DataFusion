package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 统一指标临时表实体类.
 * 用于存储从 tag_access_stats 和 tag_info 同步的统一指标数据.
 *
 * @author feng.xu
 * @version 1.0.0 , 2026/04/16
 * @since 2026/04/16
 */
@Data
@TableName("asset_lineage_metrics_un_tmp")
public class AssetLineageMetricsUnTmpEntity {

    /**
     * 主键 ID.
     * 由 tag_info.id 和 time_dimension（拆分后）组成.
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 指标名称 (tag_name).
     */
    @TableField("metric_name")
    private String metricName;

    /**
     * 指标编码 (tag_code).
     */
    @TableField("metric_code")
    private String metricCode;

    /**
     * 时间维度（日/月/年）.
     */
    private String dimension;

    /**
     * 物理层级.
     */
    @TableField("physical_level")
    private String physicalLevel;

    /**
     * 计算时效 (t0/t1).
     */
    private String timeliness;

    /**
     * API 地址.
     */
    @TableField("api_url")
    private String apiUrl;

    /**
     * 创建人.
     */
    private String creator;

    /**
     * 创建时间.
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 更新人.
     */
    private String updater;

    /**
     * 更新时间.
     */
    @TableField("update_time")
    private Date updateTime;
}
