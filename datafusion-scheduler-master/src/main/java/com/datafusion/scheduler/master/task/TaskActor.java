package com.datafusion.scheduler.master.task;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.master.ActorType;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.actor.core.AbstractActor;
import com.datafusion.scheduler.master.actor.enums.ActorStopReason;
import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;
import com.datafusion.scheduler.master.task.handler.TaskMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskMsgHandlerRegister;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务 Actor.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
@Getter
public class TaskActor extends AbstractActor {

    /**
     * ActorId (taskInstanceId).
     */
    private final String actorId;

    /**
     * 消息处理器上下文.
     */
    private final TaskMsgHandlerRegister msgHandlerContext;

    /**
     * 构造函数.
     *
     * @param taskInstanceId 任务实例ID (作为 ActorId)
     * @param msgHandlerContext 消息处理器上下文
     */
    public TaskActor(String taskInstanceId, TaskMsgHandlerRegister msgHandlerContext) {
        this.actorId = taskInstanceId;
        this.msgHandlerContext = msgHandlerContext;
    }

    @Override
    public String getActorId() {
        return actorId;
    }

    @Override
    public ActorTypeEnum type() {
        return ActorType.TASK;
    }

    @Override
    public void process(ActorMsg msg) {
        if (!(msg instanceof TaskMsg)) {
            log.warn("Invalid message type: {}", msg.getClass().getName());
            return;
        }

        TaskMsg taskMsg = (TaskMsg) msg;
        if (taskMsg.getTaskInstanceId() == null) {
            taskMsg.setTaskInstanceId(actorId);
        }
        ActionType actionType = taskMsg.getActionType();
        log.debug("TaskActor[{}] process action: {}", actorId, actionType);

        TaskMsgHandler handler = msgHandlerContext.getHandler(actionType);
        if (handler != null) {
            handler.handle(taskMsg, actorSysContext);
        } else {
            log.warn("No handler found for action type: {}", actionType);
        }
    }

    @Override
    public void init(ActorSysContext actorSysContext) {
        super.init(actorSysContext);
        log.debug("TaskActor[{}] init", actorId);
    }

    @Override
    public void destroy(ActorStopReason stopReason, Throwable cause) {
        log.debug("TaskActor[{}] destroy, reason: {}", actorId, stopReason);
    }
}
