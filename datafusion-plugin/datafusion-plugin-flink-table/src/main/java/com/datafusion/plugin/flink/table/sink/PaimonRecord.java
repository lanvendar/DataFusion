package com.datafusion.plugin.flink.table.sink;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待写入 Paimon 的单条记录和 Kafka 来源上下文.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonRecord {

    /**
     * 记录数据.
     */
    public Map<String, Object> values = new LinkedHashMap<>();

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
     * 当前 Kafka JSON 中的记录下标.
     */
    public Integer recordIndex;

    /**
     * 创建 Paimon 记录.
     *
     * @param values 记录数据
     * @param topic Kafka topic
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param recordIndex 记录下标
     * @return Paimon 记录
     */
    public static PaimonRecord of(Map<String, Object> values, String topic, Integer partition, Long offset, Integer recordIndex) {
        PaimonRecord record = new PaimonRecord();
        record.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        record.topic = topic;
        record.partition = partition;
        record.offset = offset;
        record.recordIndex = recordIndex;
        return record;
    }
}
