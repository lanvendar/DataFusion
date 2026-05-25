package com.datafusion.scheduler.master.actor.core;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.actor.Actor;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.master.actor.ActorProxy;
import com.datafusion.scheduler.master.actor.ActorSystem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 默认的 Actor 系统实现.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/13
 * @since 2026/2/13
 */
@Data
@Slf4j
public class DefaultActorSystem implements ActorSystem {
    /**
     * Actor 集合.
     */
    private final ConcurrentMap<String, ActorMailbox> actors = new ConcurrentHashMap<>();

    /**
     * 父级 Actor 映射.
     */
    private final ConcurrentMap<String, Set<String>> parentChildMap = new ConcurrentHashMap<>();

    /**
     * Actor 创建锁.
     */
    private final ConcurrentMap<String, ReentrantLock> actorCreationLocks = new ConcurrentHashMap<>();

    /**
     * Actor 配置.
     */
    private final Options actorConfig;

    /**
     * Actor 运行时配置.
     */
    private final ExecutorService executor;

    /**
     * 构造函数.
     *
     * @param executor    线程池
     * @param actorConfig Actor 配置
     */
    public DefaultActorSystem(ExecutorService executor, Options actorConfig) {
        this.actorConfig = actorConfig;
        this.executor = executor;
    }

    @Override
    public ExecutorService getActorExecutor() {
        return this.executor;
    }

    @Override
    public ActorProxy getActor(String actorId) {
        return actors.get(actorId);
    }

    @Override
    public ActorProxy createParentActor(Actor.Creator creator) {
        return createActor(creator, null);
    }

    @Override
    public ActorProxy createChildActor(Actor.Creator creator, String parentActorId) {
        return createActor(creator, parentActorId);
    }

    @Override
    public void notify(String actorId, ActorMsg actorMsg) {
        ActorMailbox mailbox = actors.get(actorId);
        if (Objects.isNull(mailbox)) {
            throw new IllegalStateException("Actor with id [" + actorId + "] is not registered!");
        }
        mailbox.notify(actorMsg);

    }

    /**
     * 关闭Actor.
     *
     * @param actorId actor id
     */
    @Override
    public void destroy(String actorId) {
        log.debug("销毁Actor,{}", actorId);
        ActorMailbox mailbox = actors.remove(actorId);
        if (mailbox != null) {
            mailbox.destroy();
        }
    }

    @Override
    public void stop() {
        log.debug("关闭Actor系统上下文");
        actors.keySet().forEach(this::destroy);

        //停止整个ActorSystem，包括Dispatcher以及清空Actor
        executor.shutdown();
        try {
            boolean terminated = executor.awaitTermination(1000L, TimeUnit.SECONDS);
            log.debug("等待Actor执行线程池关闭:{}", terminated);
        } catch (InterruptedException e) {
            log.error("dispatcher关闭失败", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
            actors.clear();
        }
    }

    @Override
    public void broadcastToChildren(String parentActorId, ActorMsg msg) {
        Set<String> children = parentChildMap.get(parentActorId);
        if (Objects.nonNull(children)) {
            children.forEach(id -> {
                try {
                    notify(id, msg);
                } catch (IllegalStateException e) {
                    log.warn("Actor is missing for {}", id);
                }
            });
        }
    }

    /**
     * 创建 Actor.
     *
     * @param creator       Actor 创建器
     * @param parentActorId 父 Actor ID
     * @return ActorProxy
     */
    private ActorProxy createActor(Actor.Creator creator, String parentActorId) {
        String actorId = creator.createActorId();
        ActorMailbox actorMailbox = actors.get(actorId);
        if (Objects.nonNull(actorMailbox)) {
            return actorMailbox;
        }

        Lock actorCreationLock =
                actorCreationLocks.computeIfAbsent(actorId, id -> new ReentrantLock());
        actorCreationLock.lock();
        try {
            actorMailbox = actors.get(actorId);
            if (Objects.nonNull(actorMailbox)) {
                return actorMailbox;
            }

            log.debug("Creating actor with id [{}]!", actorId);
            Actor actor = creator.createActor();
            ActorProxy actorProxy = null;
            if (Objects.nonNull(parentActorId)) {
                actorProxy = getActor(parentActorId);
                if (Objects.isNull(actorProxy)) {
                    throw new IllegalStateException(
                            "Parent Actor with id [" + parentActorId + "] is not registered!");
                }
            }
            ActorMailbox mailbox =
                    new ActorMailbox(actor, actorProxy, this, this.executor, this.actorConfig);
            actors.put(actorId, mailbox);
            mailbox.initActor();
            actorMailbox = mailbox;
            if (Objects.nonNull(parentActorId)) {
                parentChildMap
                        .computeIfAbsent(parentActorId, id -> ConcurrentHashMap.newKeySet())
                        .add(actorId);
            }
        } finally {
            actorCreationLock.unlock();
            actorCreationLocks.remove(actorId);
        }
        return actorMailbox;
    }
}
