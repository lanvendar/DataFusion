package com.datafusion.scheduler.exception;

/**
 * 调度异常错误枚举.
 *
 * @author 李正凯
 * @version 3.0 2022/4/28
 * @since 2022/4/28
 */
public enum SchedulerExceptionCode {

    /**
     * 提交执行任务失败.
     */
    SUBMIT_TASK_ERROR(100001, "提交执行任务失败"),

    /**
     * 停止任务失败.
     */
    STOP_TASK_ERROR(100002, "停止任务失败"),

    /**
     * 完成任务失败.
     */
    FINISH_TASK_ERROR(100003, "完成任务失败"),

    /**
     * 无法找到资源.
     */
    CANNOT_FIND_RESOURCE(100003, "无法找到资源");

    /**
     * 错误码.
     */
    private Integer code;

    /**
     * 信息.
     */
    private String message;

    /**
     * 构造方法.
     *
     * @param code    错误码
     * @param message 信息
     */
    private SchedulerExceptionCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
