package com.datafusion.plugin.flink.table.source;

import java.io.Serializable;

/**
 * 携带 Kafka 元数据的消息记录.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaRecord implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * topic 名称.
     */
    public String topic;

    /**
     * partition 编号.
     */
    public int partition;

    /**
     * offset.
     */
    public long offset;

    /**
     * 消息内容.
     */
    public String value;

    /**
     * 构造 Kafka 记录.
     */
    public KafkaRecord() {
    }

    /**
     * 构造 Kafka 记录.
     *
     * @param topic topic 名称
     * @param partition partition 编号
     * @param offset offset
     * @param value 消息内容
     */
    public KafkaRecord(String topic, int partition, long offset, String value) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.value = value;
    }
}
