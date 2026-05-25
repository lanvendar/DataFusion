package com.datafusion.scheduler.exception;

import lombok.Data;

/**
 * 调度异常类.
 *
 * @author 李正凯
 * @version 3.0 2022/4/28
 * @since 2022/4/28
 */
@Data
public class SchedulerException extends Exception {

    /**
     * 错误码.
     */
    private Integer code;

    /**
     * 信息.
     */
    private String message;

    /**
     * 提供无参数的构造方法.
     */
    public SchedulerException() {
    }

    /**
     * 构造一个基本异常.
     *
     * @param message 错误编码
     */
    public SchedulerException(String message) {
        super(message);
        this.message = message;
    }

    /**
     * 构造一个基本异常.
     *
     * @param message 错误编码
     * @param cause   异常原因
     */
    public SchedulerException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    /**
     * 构造一个基本异常.
     *
     * @param cause 异常原因
     */
    public SchedulerException(Throwable cause) {
        super(cause);
    }

    /**
     * 提供无参数的构造方法.
     *
     * @param schedulerExceptionCode 异常编码
     */
    public SchedulerException(SchedulerExceptionCode schedulerExceptionCode) {
        this.code = schedulerExceptionCode.getCode();
        this.message = schedulerExceptionCode.getMessage();
    }

}
