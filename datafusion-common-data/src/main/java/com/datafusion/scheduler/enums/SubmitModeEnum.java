package com.datafusion.scheduler.enums;

/**
 * 任务提交模式.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public enum SubmitModeEnum {
    /**
     * 同步提交，返回前已确认任务进入运行态.
     */
    SYNC,

    /**
     * 异步提交，只确认任务已接收或已排队.
     */
    ASYNC
}
