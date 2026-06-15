package com.datafusion.plugin.kafka.json.core;

/**
 * 插件内部自动维护的系统字段名.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SystemFieldNames {

    /**
     * 代理主键字段名.
     */
    public static final String PROXY_PRIMARY_KEY_FIELD = "_id_";

    /**
     * Kafka topic 字段名.
     */
    public static final String KAFKA_TOPIC_FIELD = "_kafka_topic";

    /**
     * Kafka partition 字段名.
     */
    public static final String KAFKA_PARTITION_FIELD = "_kafka_partition";

    /**
     * Kafka offset 字段名.
     */
    public static final String KAFKA_OFFSET_FIELD = "_kafka_offset";

    private SystemFieldNames() {
    }
}
