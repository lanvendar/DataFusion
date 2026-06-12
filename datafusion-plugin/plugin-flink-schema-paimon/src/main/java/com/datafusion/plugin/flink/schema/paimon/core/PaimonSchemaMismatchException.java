package com.datafusion.plugin.flink.schema.paimon.core;

/**
 * Paimon 表结构不兼容异常.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonSchemaMismatchException extends FlinkSchemaPaimonException {

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
}
