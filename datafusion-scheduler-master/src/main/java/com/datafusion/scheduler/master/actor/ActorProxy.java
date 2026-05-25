package com.datafusion.scheduler.master.actor;

/**
 * Actor 行为代理接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/13
 * @since 2026/2/13
 */
public interface ActorProxy {
    /**
     * 获取actor唯一标识对象.
     *
     * @return actor唯一标识对象
     */
    String getActorId();

    /**
     * 消息通知.
     *
     * @param actorMsg 消息
     */
    void notify(ActorMsg actorMsg);
}
