package com.datafusion.manager.asset.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 指标同步记录实体类.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/22
 * @since 2026/01/22
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("metric_sync_record")
public class MetricSyncRecordEntity extends BaseIdEntity {

    /**
     * 同步开始时间.
     */
    @TableField("sync_start_time")
    private java.util.Date syncStartTime;

    /**
     * 同步结束时间.
     */
    @TableField("sync_end_time")
    private java.util.Date syncEndTime;

    /**
     * 同步状态：0-进行中 1-成功 2-部分成功 3-失败.
     */
    @TableField("sync_status")
    private Integer syncStatus;

    /**
     * 同步类型：DATE-按日期 CODE-按编码 BATCH-批量.
     */
    @TableField("sync_type")
    private String syncType;

    /**
     * 同步参数（JSON格式）.
     */
    @TableField("sync_param")
    private String syncParam;

    /**
     * 同步总数.
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 成功数.
     */
    @TableField("success_count")
    private Integer successCount;

    /**
     * 失败数.
     */
    @TableField("fail_count")
    private Integer failCount;

    /**
     * 失败的thirdMetricId列表，逗号分隔.
     */
    @TableField("fail_metric_ids")
    private String failMetricIds;

}
