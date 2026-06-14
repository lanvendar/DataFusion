package com.datafusion.plugin.kafka.json.core;

/**
 * Flink schema Paimon 插件异常.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaJsonPaimonException extends RuntimeException {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造插件异常.
     *
     * @param message 异常消息
     */
    public KafkaJsonPaimonException(String message) {
        super(message);
    }

    /**
     * 构造插件异常.
     *
     * @param message 异常消息
     * @param cause 根因
     */
    public KafkaJsonPaimonException(String message, Throwable cause) {
        super(message, cause);
    }
}
