package com.datafusion.plugin.flink.schema.paimon.source;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Kafka ConsumerRecord 到插件记录的反序列化器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaRecordDeserializer implements KafkaRecordDeserializationSchema<KafkaRecord> {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 反序列化 Kafka record.
     *
     * @param record Kafka record
     * @param out 输出 collector
     * @throws IOException 反序列化异常
     */
    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<KafkaRecord> out) throws IOException {
        String value = record.value() == null ? null : new String(record.value(), StandardCharsets.UTF_8);
        out.collect(new KafkaRecord(record.topic(), record.partition(), record.offset(), value));
    }

    /**
     * 获取输出类型.
     *
     * @return 输出类型
     */
    @Override
    public TypeInformation<KafkaRecord> getProducedType() {
        return TypeInformation.of(KafkaRecord.class);
    }
}
