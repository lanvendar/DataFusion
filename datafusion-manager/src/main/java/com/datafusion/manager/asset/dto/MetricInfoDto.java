package com.datafusion.manager.asset.dto;

import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 指标信息DTO.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/21
 * @since 2026/01/21
 */
@Data
public class MetricInfoDto {

    /**
     * 外部指标唯一ID.
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
     * 数据源名称.
     */
    private String datasourceName;

    /**
     * 父API资源id.
     */
    private UUID parentResourceId;

    /**
     * api资源快照.
     */
    private ResourceSnapshotBuilder.ApiResourceSnapshot apiResourceSnapshot;

    /**
     * 字段信息.
     */
    private List<MetricColumnInfo> columnInfoList;

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
