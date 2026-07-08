package com.datafusion.plugin.kafka.json.core;

/**
 * Paimon 表结构不兼容异常.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonSchemaMismatchException extends KafkaJsonPaimonException {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造表结构不兼容异常.
     *
     * @param message 异常消息
     */
    public PaimonSchemaMismatchException(String message) {
        super(message);
    }

    /**
     * 构造表结构不兼容异常.
     *
     * @param message 异常消息
     * @param cause 根因
     */
    public PaimonSchemaMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
