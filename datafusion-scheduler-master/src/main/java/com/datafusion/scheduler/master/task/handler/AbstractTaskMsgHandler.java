package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.task.TaskExecutor;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 任务消息处理器抽象基类.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
@Getter
public abstract class AbstractTaskMsgHandler implements TaskMsgHandler {

    /**
     * 任务存储.
     */
    protected TaskStorage taskStorage;

    /**
     * 全局事件操作.
     */
    protected final GlobalEventOperator eventOperator;

    /**
     * 任务执行器.
     */
    protected final TaskExecutor taskExecutor;

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     */
    protected AbstractTaskMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, TaskExecutor taskExecutor) {
        this.taskStorage = taskStorage;
        this.eventOperator = eventOperator;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void handle(TaskMsg msg, ActorSysContext context) {
        log.debug("收到任务消息: msg=[{}]", msg);
        String taskInstanceId = msg.getTaskInstanceId();

        // 从存储获取任务实例
        TaskInstance taskInstance = getTaskInstance(taskInstanceId);

        if (taskInstance == null) {
            //注意:只有初始化init的action,且实例未生成的时候走此逻辑,即第一次初始化
            log.warn("任务实例不存在,初始化任务实例:: taskInstanceId=[{}]", taskInstanceId);
            if (msg.getMsgType().equals(ActionType.INIT.taskType())) {
                this.handleAction(msg, null);
            }
            return;
        }

        StatusEnum currentState = taskInstance.getState();
        log.info("[{}] - 任务实例, 当前任务状态: {}", taskInstanceId, currentState);

        // 根据手工/自动动作获取前置状态
        Set<StatusEnum> preState = msg.isManualAction() ? getManualPreState() : getPreState();

        if (preState == null) {
            log.info("不支持触发任务动作: 动作类型是否手工=[{}], taskInstanceId=[{}], 当前状态=[{}]",
                    msg.isManualAction(), taskInstanceId, currentState);
        } else if (preState.contains(currentState)) {
            log.info("触发任务动作: 动作类型是否手工=[{}], taskInstanceId=[{}], 当前状态=[{}]",
                    msg.isManualAction(), taskInstanceId, currentState);
            if (msg.isManualAction()) {
                this.handleManualAction(msg, context);
            } else {
                this.handleAction(msg, context);
            }
        } else {
            log.warn("无法执行 [{}] 动作, taskInstanceId=[{}], 当前状态=[{}]",
                    msg.getActionType(), taskInstanceId, currentState);
        }
    }

    /**
     * 执行动作.
     *
     * @param msg     消息
     * @param context actor 上下文
     */
    protected abstract void handleAction(TaskMsg msg, ActorSysContext context);

    /**
     * 处理手工动作.
     *
     * @param msg     消息
     * @param context actor 上下文
     */
    protected abstract void handleManualAction(TaskMsg msg, ActorSysContext context);

    /**
     * 获取任务实例.
     *
     * @param taskInstanceId 任务实例ID
     * @return 任务实例
     */
    protected TaskInstance getTaskInstance(String taskInstanceId) {
        return taskStorage.getInstanceById(taskInstanceId);
    }

    /**
     * 获取任务信息.
     *
     * @param taskId 任务ID
     * @return 任务信息
     */
    protected TaskInfo getTaskInfo(String taskId) {
        return taskStorage.getTaskInfo(taskId);
    }

    /**
     * 保存任务实例.
     *
     * @param taskInstance 任务实例
     */
    protected void saveTaskInstance(TaskInstance taskInstance) {
        taskStorage.saveInstance(taskInstance);
    }

    /**
     * 删除任务实例.
     *
     * @param taskInstanceId 任务实例ID
     */
    protected void removeInstance(String taskInstanceId) {
        taskStorage.removeInstanceById(taskInstanceId);
    }

    /**
     * 通知流程 Actor 状态变化.
     *
     * @param msg     流程消息
     * @param context actor 上下文
     */
    protected void notifyFlowActor(FlowMsg msg, ActorSysContext context) {
        if (context != null && context.getParentActor() != null) {
            context.getParentActor().notify(msg);
            //context.notify(msg.getFlowInstanceId(), msg);
        }
    }
}
