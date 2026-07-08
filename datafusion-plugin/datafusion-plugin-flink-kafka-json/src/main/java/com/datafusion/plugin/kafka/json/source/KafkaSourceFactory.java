package com.datafusion.plugin.kafka.json.source;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.KafkaSourceConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceBuilder;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Flink KafkaSource 工厂.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaSourceFactory {

    /**
     * timestamp 位点属性名.
     */
    private static final String STARTING_TIMESTAMP_MS = "starting.timestamp.ms";

    /**
     * 创建 Kafka source.
     *
     * @param source source 配置
     * @return Kafka source
     */
    public KafkaSource<KafkaRecord> create(KafkaSourceConfig source) {
        KafkaSourceBuilder<KafkaRecord> builder = KafkaSource.<KafkaRecord>builder()
                .setBootstrapServers(source.bootstrapServers)
                .setGroupId(source.groupId)
                .setDeserializer(new KafkaRecordDeserializer())
                .setStartingOffsets(startingOffsets(source));
        if (source.topics != null && !source.topics.isEmpty()) {
            builder.setTopics(source.topics);
        } else {
            builder.setTopicPattern(Pattern.compile(source.topicPattern));
        }
        Properties properties = new Properties();
        if (source.properties != null) {
            properties.putAll(source.properties);
        }
        builder.setProperties(properties);
        return builder.build();
    }

    private OffsetsInitializer startingOffsets(KafkaSourceConfig source) {
        String value = TextUtils.upper(source.startingOffsets, "GROUP-OFFSETS").replace('_', '-');
        if ("EARLIEST".equals(value)) {
            return OffsetsInitializer.earliest();
        }
        if ("LATEST".equals(value)) {
            return OffsetsInitializer.latest();
        }
        if ("TIMESTAMP".equals(value)) {
            return OffsetsInitializer.timestamp(startingTimestampMs(source));
        }
        if ("GROUP-OFFSETS".equals(value)) {
            return OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST);
        }
        throw new KafkaJsonPaimonException("Unsupported source.startingOffsets: " + source.startingOffsets);
    }

    private long startingTimestampMs(KafkaSourceConfig source) {
        String value = source.properties == null ? null : source.properties.get(STARTING_TIMESTAMP_MS);
        if (TextUtils.isBlank(value)) {
            throw new KafkaJsonPaimonException("source.properties.starting.timestamp.ms is required for timestamp offsets");
        }
        return Long.parseLong(value);
    }
}
