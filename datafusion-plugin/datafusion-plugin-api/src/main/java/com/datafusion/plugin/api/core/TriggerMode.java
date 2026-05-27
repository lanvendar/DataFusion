package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.util.TextUtils;

/**
 * API 抽取任务触发模式.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public enum TriggerMode {
    /** 按 cron 表达式在本进程中持续触发. */
    CRON,

    /** 立即执行一次,用于调试或调度系统触发. */
    ONCE,

    /** 调度系统触发一次,兼容设计文档中的调度接入描述. */
    SCHEDULER;

    /**
     * 解析触发模式.
     *
     * @param value 配置值
     * @return 触发模式
     */
    public static TriggerMode parse(String value) {
        String normalized = TextUtils.upper(value, "ONCE");
        try {
            return TriggerMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ApiExtractException("Unsupported trigger.mode: " + value);
        }
    }

    /**
     * 是否只执行一次.
     *
     * @return true 表示单次执行
     */
    public boolean isSingleRun() {
        return this == ONCE || this == SCHEDULER;
    }
}
