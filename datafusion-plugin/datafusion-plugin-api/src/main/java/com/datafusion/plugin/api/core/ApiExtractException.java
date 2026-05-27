package com.datafusion.plugin.api.core;

/**
 * API 抽取异常类.
 *
 * <p>
 * 用于标识 API 抽取过程中的业务异常.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ApiExtractException extends RuntimeException {
    
    /**
     * 构造异常.
     *
     * @param message 异常消息
     */
    public ApiExtractException(String message) {
        super(message);
    }

    /**
     * 构造异常.
     *
     * @param message 异常消息
     * @param cause 异常原因
     */
    public ApiExtractException(String message, Throwable cause) {
        super(message, cause);
    }
}
