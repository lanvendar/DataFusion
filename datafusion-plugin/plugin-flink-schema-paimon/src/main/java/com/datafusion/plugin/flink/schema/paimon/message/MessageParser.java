package com.datafusion.plugin.flink.schema.paimon.message;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Kafka 消息解析器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class MessageParser {

    /**
     * JSON 解析器.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 解析 Kafka 消息.
     *
     * @param message 原始消息
     * @return Kafka envelope
     */
    public KafkaEnvelope parse(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new FlinkSchemaPaimonException("Kafka message is blank");
        }
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (IOException e) {
            throw new FlinkSchemaPaimonException("Failed to parse Kafka message", e);
        }
    }
}
