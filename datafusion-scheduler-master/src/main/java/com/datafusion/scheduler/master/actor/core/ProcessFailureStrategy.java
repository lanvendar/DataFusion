package com.datafusion.scheduler.master.actor.core;

import lombok.Getter;
import lombok.ToString;

/**
 * actor 处理失败策略.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */

@Getter
@ToString
public class ProcessFailureStrategy {
    /**
     * 是否停止.
     */
    private final boolean stop;

    /**
     * 重试间隔.
     */
    private ProcessFailureStrategy(boolean stop) {
        this.stop = stop;
    }

    /**
     * 停止.
     *
     * @return 停止
     */
    public static ProcessFailureStrategy stop() {
        return new ProcessFailureStrategy(true);
    }

    /**
     * 重试间隔.
     *
     * @return 重试间隔
     */
    public static ProcessFailureStrategy resume() {
        return new ProcessFailureStrategy(false);
    }
}
