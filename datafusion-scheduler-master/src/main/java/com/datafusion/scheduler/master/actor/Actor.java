package com.datafusion.scheduler.master.actor;

import com.datafusion.scheduler.master.actor.core.InitFailureStrategy;
import com.datafusion.scheduler.master.actor.core.ProcessFailureStrategy;
import com.datafusion.scheduler.master.actor.enums.ActorStopReason;
import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;

/**
 * Actor 模型接口定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/12
 * @since 2026/2/12
 */
public interface Actor {
    /**
     * 获取actor唯一标识对象.
     *
     * @return actor唯一标识对象
     */
    String getActorId();

    /**
     * 获取actor类型接口.
     *
     * @return actor类型
     */
    ActorTypeEnum type();

    /**
     * 获取actor代理对象.
     *
     * @return actor代理对象
     */
    ActorProxy getActorProxy();

    /**
     * 处理消息.
     *
     * @param msg 消息
     */
    void process(ActorMsg msg);

    /**
     * 初始化.
     *
     * @param actorSysContext actor系统上下文
     */
    default void init(ActorSysContext actorSysContext) {
    }

    /**
     * 销毁.
     *
     * @param stopReason 停止原因
     * @param cause      异常
     */
    default void destroy(ActorStopReason stopReason, Throwable cause) {
    }

    /**
     * 处理异常.
     *
     * @param attempt 尝试次数
     * @param t       异常
     * @return 初始化失败策略
     */
    default InitFailureStrategy onInitFailure(int attempt, Throwable t) {
        if (t instanceof Error) {
            return InitFailureStrategy.stop();
        }
        return InitFailureStrategy.retryWithDelay(5000L * attempt);
    }

    /**
     * 处理异常.
     *
     * @param msg 消息
     * @param t   异常
     * @return 处理失败策略
     */
    default ProcessFailureStrategy onProcessFailure(ActorMsg msg, Throwable t) {
        if (t instanceof Error) {
            return ProcessFailureStrategy.stop();
        } else {
            return ProcessFailureStrategy.resume();
        }
    }

    /**
     * 创建者接口定义.
     */
    interface Creator {
        /**
         * 创建actor唯一标识对象.
         *
         * @return actor唯一标识对象
         */
        String createActorId();

        /**
         * 创建actor.
         *
         * @return actor
         */
        Actor createActor();
    }
}
