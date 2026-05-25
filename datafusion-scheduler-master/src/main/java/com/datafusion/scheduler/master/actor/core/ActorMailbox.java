package com.datafusion.scheduler.master.actor.core;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.actor.Actor;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.master.actor.ActorProxy;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.actor.ActorSystem;
import com.datafusion.scheduler.master.actor.enums.ActorStopReason;
import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Actor 模型接口定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */
@Slf4j
public final class ActorMailbox implements ActorSysContext {

    /**
     * Actor系统.
     */
    @Getter
    private final ActorSystem system;

    /**
     * Actor 执行线程池.
     */
    @Getter
    private final ExecutorService executor;

    /**
     * Actor 配置.
     */
    @Getter
    private final Options actorConfig;

    /**
     * 每次拉取消息的数量.
     */
    private final int msgPollNum;

    /**
     * 初始化失败重试最大次数.
     */
    private final int maxInitAttempts;

    /**
     * Actor.
     */
    private final Actor actor;

    /**
     * 父Actor.
     */
    private final ActorProxy parentActor;

    /**
     * 队列.
     */
    private final ConcurrentLinkedQueue<ActorMsg> queue = new ConcurrentLinkedQueue<>();

    /**
     * 繁忙状态.
     */
    private final AtomicBoolean busy = new AtomicBoolean(FREE);

    /**
     * 繁忙状态.
     */
    private static final boolean FREE = false;

    /**
     * 繁忙状态.
     */
    private static final boolean BUSY = true;

    /**
     * 就绪状态.
     */
    private final AtomicBoolean ready = new AtomicBoolean(NOT_READY);

    /**
     * 就绪状态.
     */
    private static final boolean NOT_READY = false;

    /**
     * 就绪状态.
     */
    private static final boolean READY = true;

    /**
     * 销毁中状态.
     */
    private final AtomicBoolean destroyInProgress = new AtomicBoolean();

    /**
     * 构造函数.
     *
     * @param system      Actor系统
     * @param actor       Actor
     * @param executor    Actor执行线程池
     * @param actorConfig Actor配置
     */
    public ActorMailbox(Actor actor, ActorSystem system, ExecutorService executor, Options actorConfig) {
        this(actor, null, system, executor, actorConfig);
    }

    /**
     * 构造函数.
     *
     * @param system      Actor系统
     * @param actor       Actor
     * @param parentActor 父Actor
     * @param executor    Actor执行线程池
     * @param actorConfig Actor配置
     */
    public ActorMailbox(Actor actor, ActorProxy parentActor, ActorSystem system, ExecutorService executor, Options actorConfig) {
        this.system = system;
        this.actor = actor;
        this.parentActor = parentActor;
        this.executor = executor;
        this.actorConfig = actorConfig;
        this.msgPollNum = actorConfig.get(MasterConfigOptions.ACTOR_MSG_POLL_NUM);
        this.maxInitAttempts = actorConfig.get(MasterConfigOptions.ACTOR_INIT_MAX_ATTEMPTS);
    }

    /**
     * 初始化Actor.
     */
    public void initActor() {
        executor.execute(() -> tryInit(1));
    }

    private void tryInit(int attempt) {
        try {
            log.debug("[{}] Trying to init actor, attempt: {}", actor.getActorId(), attempt);
            if (!destroyInProgress.get()) {
                actor.init(this);
                if (!destroyInProgress.get()) {
                    ready.set(READY);
                    tryProcessQueue(false);
                }
            }
        } catch (Throwable t) {
            InitFailureStrategy strategy;
            int attemptNum = attempt + 1;
            if (isUnrecoverable(t)) {
                strategy = InitFailureStrategy.stop();
            } else {
                log.debug("[{}] Failed to init actor, attempt: {}", actor.getActorId(), attempt, t);
                strategy = actor.onInitFailure(attempt, t);
            }
            if (strategy.isStop() || attemptNum > maxInitAttempts) {
                log.info("[{}] Failed to init actor, attempt {}, going to stop attempts.", actor.getActorId(), attempt, t);
                destroy(ActorStopReason.INIT_FAILED, t.getCause());
            } else if (strategy.getRetryDelay() > 0) {
                log.info("[{}] Failed to init actor, attempt {}, going to retry in attempts in {}ms",
                        actor.getActorId(), attempt, strategy.getRetryDelay());
                // TODO 延迟执行
                //system.getActorExecutor().execute(() -> tryInit(attemptNum), strategy.getRetryDelay(), TimeUnit.MILLISECONDS);
            } else {
                log.info("[{}] Failed to init actor, attempt {}, going to retry immediately", actor.getActorId(), attempt);
                executor.execute(() -> tryInit(attemptNum));
            }
        }
    }

    private static boolean isUnrecoverable(Throwable t) {
        Throwable current = t;
        while (Objects.nonNull(current)) {
            if (current instanceof Error) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 添加消息.
     *
     * @param msg 消息
     */
    private void enqueue(ActorMsg msg) {
        if (!destroyInProgress.get()) {
            queue.add(msg);
            tryProcessQueue(true);
        } else {
            log.warn("[{}] Mailbox销毁中, 无法发送消息", actor.getActorId());
        }
    }

    /**
     * 处理队列.
     *
     * @param newMsg 是否是新消息
     */
    private void tryProcessQueue(boolean newMsg) {
        // 当前邮箱还未就绪
        if (ready.get() != READY) {
            log.trace("[{}] MailBox is not ready, new msg: {}", actor.getActorId(), newMsg);
            return;
        }
        // 当前消息不是新的，且邮箱为空
        if (!newMsg && queue.isEmpty()) {
            log.trace("[{}] MailBox is empty, new msg: {}", actor.getActorId(), false);
            return;
        }
        // 当前消息是新消息，或者邮箱非空，则通过CAS方式设置邮箱为繁忙状态。CAS竞争失败则表示当前邮箱正在处理消息
        if (busy.compareAndSet(FREE, BUSY)) {
            executor.execute(this::processMailbox);
        } else {
            log.trace("[{}] MailBox is busy, new msg: {}", actor.getActorId(), newMsg);
        }
    }

    /**
     * 处理消息.
     */
    private void processMailbox() {
        boolean noMoreElements = false;
        for (int i = 0; i < msgPollNum; i++) {
            ActorMsg msg = queue.poll();
            if (Objects.nonNull(msg)) {
                try {
                    log.debug("[{}] Going to process message: {}", actor.getActorId(), msg);
                    actor.process(msg);
                } catch (Throwable t) {
                    log.debug("[{}] Failed to process message: {}", actor.getActorId(), msg, t);
                    ProcessFailureStrategy strategy = actor.onProcessFailure(msg, t);
                    if (strategy.isStop()) {
                        system.stop();
                    }
                }
            } else {
                noMoreElements = true;
                break;
            }
        }
        if (noMoreElements) {
            busy.set(FREE);
            executor.execute(() -> tryProcessQueue(false));
        } else {
            executor.execute(this::processMailbox);
        }
    }

    /**
     * 销毁.
     *
     * @param cause      销毁原因
     * @param stopReason 停止原因
     */
    private void destroy(ActorStopReason stopReason, Throwable cause) {
        if (Objects.isNull(stopReason)) {
            stopReason = ActorStopReason.UNKNOWN;
        }
        destroyInProgress.set(true);
        ActorStopReason finalStopReason = stopReason;
        executor.execute(() -> {
            try {
                ready.set(NOT_READY);
                actor.destroy(finalStopReason, cause);
                queue.forEach(msg -> log.info("[{}] Destroyed, stopReason: {},message: {}", actor.getActorId(), finalStopReason.name(), msg));
            } catch (Throwable ignore) {
            }
        });
    }

    @Override
    public void destroy() {
        destroy(ActorStopReason.ACTOR_SYSTEM_STOPPED, null);
    }

    @Override
    public Actor getSelf() {
        return actor;
    }

    @Override
    public ActorProxy getParentActor() {
        return parentActor;
    }

    /**
     * Actor 行为代理 ActorProxy 接口的实现.
     *
     * @return actor唯一标识对象
     */
    @Override
    public String getActorId() {
        return actor.getActorId();
    }

    /**
     * Actor 行为代理 ActorProxy 接口的实现.
     *
     * @param actorMsg 消息
     */
    @Override
    public void notify(ActorMsg actorMsg) {
        enqueue(actorMsg);
    }

    @Override
    public void notify(String actorId, ActorMsg actorMsg) {
        system.notify(actorId, actorMsg);
    }

    @Override
    public void notify(List<String> targetActorIds, ActorMsg actorMsg) {
        targetActorIds.forEach(targetActorId -> notify(targetActorId, actorMsg));
    }

    @Override
    public ActorProxy getOrCreateChildActor(Supplier<Actor.Creator> creator, String parentActorId) {
        return system.createChildActor(creator.get(), parentActorId);
    }

    @Override
    public void broadcastToChildren(ActorMsg msg) {
        system.broadcastToChildren(getActorId(), msg);
    }

    @Override
    public void broadcastToChildrenByType(ActorMsg msg, ActorTypeEnum actorType) {
        // TODO
    }

    @Override
    public List<String> filterToActorIds(Predicate<String> childFilter) {
        // TODO
        return List.of();
    }
}
