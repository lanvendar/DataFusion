package com.datafusion.scheduler.master.actor.core;

import lombok.Getter;
import lombok.ToString;

/**
 * actor 初始化失败策略.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */

@Getter
@ToString
public class InitFailureStrategy {
    /**
     * 是否停止.
     */
    private final boolean stop;

    /**
     * 重试间隔.
     */
    private final long retryDelay;

    /**
     * 构造函数.
     *
     * @param stop       是否停止
     * @param retryDelay 重试间隔
     */
    private InitFailureStrategy(boolean stop, long retryDelay) {
        this.stop = stop;
        this.retryDelay = retryDelay;
    }

    /**
     * 重试间隔为0.
     *
     * @return 重试间隔为0
     */
    public static InitFailureStrategy retryImmediately() {
        return retryWithDelay(0);
    }

    /**
     * 重试间隔.
     *
     * @param ms 重试间隔
     * @return 重试间隔
     */
    public static InitFailureStrategy retryWithDelay(long ms) {
        return new InitFailureStrategy(false, ms);
    }

    /**
     * 停止.
     *
     * @return 停止
     */
    public static InitFailureStrategy stop() {
        return new InitFailureStrategy(true, 0);
    }
}
