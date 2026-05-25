package com.datafusion.scheduler.master.actor.enums;

/**
 * 停止原因枚举.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */
public enum ActorStopReason {
    /**
     * 初始化失败.
     */
    INIT_FAILED,
    /**
     * 正常停止.
     */
    STOPPED,
    /**
     * Actor系统停止.
     */
    ACTOR_SYSTEM_STOPPED,
    /**
     * 未知停止原因.
     */
    UNKNOWN;
}
