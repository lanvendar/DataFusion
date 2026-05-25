package com.datafusion.scheduler.master.actor;

import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Actor 系统上下文.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */
public interface ActorSysContext extends ActorProxy {
    /**
     * 获取自己的唯一标识.
     *
     * @return actor对象
     */
    Actor getSelf();

    /**
     * 获取父级 Actor的代理对象.
     *
     * @return actor对象
     */
    ActorProxy getParentActor();

    /**
     * 向目标actor通知消息.
     *
     * @param actorId  目标actor的唯一标识
     * @param actorMsg 消息
     */
    void notify(String actorId, ActorMsg actorMsg);

    /**
     * 向目标actor群发消息.
     *
     * @param actorIdList actor唯一标识列表
     * @param actorMsg    消息
     */
    void notify(List<String> actorIdList, ActorMsg actorMsg);

    /**
     * 销毁目标actor.
     */
    void destroy();

    /**
     * 获取或者创建子Actor代理对象.
     *
     * @param creator       actor创建器
     * @param parentActorId actor唯一标识
     * @return ActorRef
     */
    ActorProxy getOrCreateChildActor(Supplier<Actor.Creator> creator, String parentActorId);

    /**
     * 向actor广播消息.
     *
     * @param msg 消息
     */
    void broadcastToChildren(ActorMsg msg);

    /**
     * 向actor广播消息.
     *
     * @param msg       消息
     * @param actorType actor业务类型
     */
    void broadcastToChildrenByType(ActorMsg msg, ActorTypeEnum actorType);

    /**
     * 过滤Actor.
     *
     * @param childFilter 过滤条件
     * @return 子ActorId列表
     */
    List<String> filterToActorIds(Predicate<String> childFilter);
}
