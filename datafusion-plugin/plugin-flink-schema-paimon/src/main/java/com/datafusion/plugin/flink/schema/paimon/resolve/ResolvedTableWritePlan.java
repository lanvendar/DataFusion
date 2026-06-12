package com.datafusion.plugin.flink.schema.paimon.resolve;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单表写入计划.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ResolvedTableWritePlan implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 目标表配置.
     */
    public ResolvedTableConfig tableConfig = new ResolvedTableConfig();

    /**
     * 待写入记录.
     */
    public List<Map<String, Object>> records = new ArrayList<>();

    /**
     * Kafka topic.
     */
    public String topic;

    /**
     * Kafka partition.
     */
    public Integer partition;

    /**
     * Kafka offset.
     */
    public Long offset;

    /**
     * 创建记录副本.
     *
     * @param record 原始记录
     * @return 记录副本
     */
    public static Map<String, Object> copyRecord(Map<String, Object> record) {
        return record == null ? new LinkedHashMap<>() : new LinkedHashMap<>(record);
    }
}
