package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.task.TaskMsg;

import java.util.Set;

/**
 * 任务消息处理器接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
public interface TaskMsgHandler {

    /**
     * 获取动作类型.
     *
     * @return 动作类型
     */
    ActionType getActionType();

    /**
     * 处理消息.
     *
     * @param msg 消息
     * @param context actor 上下文
     */
    void handle(TaskMsg msg, ActorSysContext context);

    /**
     * 获取自动动作的前置状态集合.
     *
     * @return 前置状态集合
     */
    default Set<StatusEnum> getPreState() {
        return null;
    }

    /**
     * 获取手工动作的前置状态集合.
     *
     * @return 前置状态集合
     */
    default Set<StatusEnum> getManualPreState() {
        return null;
    }
}
