package com.datafusion.manager.asset.po;

import lombok.Data;

/**
 * 指标信息DTO.
 * 用于从dw_tag_info和tag_info表查询指标数据.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/21
 * @since 2026/01/21
 */
@Data
public class MetricInfoEntity {
    /**
     * 指标ID.
     */
    private String thirdMetricId;

    /**
     * 指标编码.
     */
    private String metricCode;

    /**
     * 指标名称.
     */
    private String metricName;

    /**
     * 时间维度（日/月/年）.
     */
    private String dimension;

    /**
     * 字段名称.
     */
    private String columnName;

    /**
     * API地址.
     */
    private String apiUrl;

    /**
     * 表名称.
     */
    private String tableName;

    /**
     * 物理层级.
     */
    private String physicalLevel;

    /**
     * 计算时效.
     */
    private String timeliness;

    /**
     * 类型.
     */
    private String type;

    /**
     * 统一指标主键id.
     */
    private String tagInfoId;

}
