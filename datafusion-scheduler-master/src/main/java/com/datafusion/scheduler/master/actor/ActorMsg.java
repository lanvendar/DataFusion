package com.datafusion.scheduler.master.actor;

/**
 * actor 的 消息类型.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */
public interface ActorMsg {
    /**
     * 获取消息类型.
     *
     * @return 消息类型
     */
    String getMsgType();
}
