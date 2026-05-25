package com.datafusion.scheduler.master.actor;

import java.util.concurrent.ExecutorService;

/**
 * Actor 系统功能接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */
public interface ActorSystem {
    /**
     * 获取actor分发执行器线程池.
     *
     * @return actor执行器线程池
     */
    ExecutorService getActorExecutor();

    /**
     * 获取actor调度执行器线程池.
     *
     * @param actorId actor的唯一标识
     * @return actor调度执行器线程池
     */
    ActorProxy getActor(String actorId);

    /**
     * 创建父actor.
     *
     * @param creator 创建者
     * @return actor
     */
    ActorProxy createParentActor(Actor.Creator creator);

    /**
     * 创建子actor.
     *
     * @param creator       创建者
     * @param parentActorId 父actor唯一标识
     * @return actor
     */
    ActorProxy createChildActor(Actor.Creator creator, String parentActorId);

    /**
     * 停止actor.
     *
     * @param actorId actor唯一标识
     */
    void destroy(String actorId);

    /**
     * 通知actor.
     *
     * @param actorId  actor唯一标识
     * @param actorMsg actor消息
     */
    void notify(String actorId, ActorMsg actorMsg);

    /**
     * 向actor群发消息.
     *
     * @param parentActorId 父actor唯一标识
     * @param msg           消息
     */
    void broadcastToChildren(String parentActorId, ActorMsg msg);

    /**
     * 停止actor系统.
     */
    void stop();
}
