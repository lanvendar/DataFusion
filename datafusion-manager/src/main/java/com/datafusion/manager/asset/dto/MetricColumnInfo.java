package com.datafusion.manager.asset.dto;

import lombok.Data;

/**
 * 指标选择表信息.
 * @author xufeng
 * @version 1.0.0, 2026/3/31
 * @since 2026/3/31
 */
@Data
public class MetricColumnInfo {

    /**
     * 字段名称.
     */
    private String columnName;

    /**
     * 表名称.
     */
    private String tableName;

    /**
     * 数据源名称.
     */
    private String datasourceName;
}
