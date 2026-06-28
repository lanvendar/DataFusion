package com.datafusion.scheduler.master.task.handler;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 任务运行消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskRunMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskRunMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.RUN;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        return EnumSet.of(StatusEnum.SUBMITTING, StatusEnum.SUBMIT_FAILURE, StatusEnum.SUBMIT_SUCCESS,
                StatusEnum.RUNNING, StatusEnum.STOPPING, StatusEnum.KILLING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        return null;
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = super.getTaskInstance(msg.getTaskInstanceId());
        TaskResult acceptState = msg.getTaskResult();
        if (acceptState == null || acceptState.getTaskState() == null) {
            log.info("[{}] - 收到任务运行消息但无worker结果, 保持当前状态: {}",
                    taskIns.getInstanceId(), taskIns.getState());
            return;
        }

        StatusEnum targetState = acceptState.getTaskState();
        if (targetState == StatusEnum.SUBMITTING || targetState == StatusEnum.SUBMIT_SUCCESS) {
            updateTaskState(taskIns, acceptState, targetState, context);
            return;
        }
        if (targetState == StatusEnum.RUNNING) {
            handleRunning(taskIns, acceptState, context);
            return;
        }
        if (targetState == StatusEnum.RUN_SUCCESS) {
            handleRunSuccess(taskIns, acceptState, context);
            return;
        }
        if (targetState == StatusEnum.UNKNOWN) {
            handleUnknown(taskIns, acceptState, context);
            return;
        }
        if (targetState == StatusEnum.SUBMIT_FAILURE || targetState == StatusEnum.RUN_FAILURE) {
            handleFailure(taskIns, acceptState, targetState, context);
        }
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        log.error("不可能发生!!!程序异常!!!");
    }

    private void handleRunning(TaskInstance taskIns, TaskResult taskResult, ActorSysContext context) {
        if (taskIns.getStartTime() == null || taskIns.getStartTime() <= 0) {
            taskIns.setStartTime(System.currentTimeMillis());
        }
        updateTaskState(taskIns, taskResult, StatusEnum.RUNNING, context);
    }

    private void handleRunSuccess(TaskInstance taskIns, TaskResult taskResult, ActorSysContext context) {
        try {
            super.masterTaskOperator.finishTask(taskIns);
        } catch (Exception e) {
            log.error("任务实例无法完成:taskInsId=[{}]", taskIns.getInstanceId());
        }
        long endTime = System.currentTimeMillis();
        if (taskIns.getStartTime() == null || taskIns.getStartTime() <= 0) {
            taskIns.setStartTime(endTime);
        }
        taskIns.setState(StatusEnum.RUN_SUCCESS);
        taskIns.setEndTime(endTime);
        taskIns.setCostTime(endTime - taskIns.getStartTime());
        taskIns.setTaskResult(taskResult);
        sendTaskGlobalEventIfNeeded(taskIns);
        super.saveTaskInstance(taskIns);
        notifyFlow(taskIns, StatusEnum.RUN_SUCCESS, context);
        notifyNextTasks(taskIns, context);
    }

    private void handleFailure(TaskInstance taskIns, TaskResult taskResult, StatusEnum targetState,
            ActorSysContext context) {
        long endTime = System.currentTimeMillis();
        if (targetState == StatusEnum.RUN_FAILURE) {
            if (taskIns.getStartTime() == null || taskIns.getStartTime() <= 0) {
                taskIns.setStartTime(endTime);
            }
            taskIns.setEndTime(endTime);
            taskIns.setCostTime(endTime - taskIns.getStartTime());
        }
        taskIns.setState(targetState);
        taskIns.setTaskResult(taskResult);
        super.saveTaskInstance(taskIns);
        TaskMsg taskMsg = TaskMsg.builder()//
                .flowInstanceId(taskIns.getFlowInstanceId())//
                .taskInstanceId(taskIns.getInstanceId())//
                .actionType(ActionType.RESTART)//
                .isManualAction(false)//
                .build();
        context.notify(taskMsg);
    }

    private void handleUnknown(TaskInstance taskIns, TaskResult taskResult, ActorSysContext context) {
        long endTime = System.currentTimeMillis();
        if (taskIns.getStartTime() == null || taskIns.getStartTime() <= 0) {
            taskIns.setStartTime(endTime);
        }
        taskIns.setState(StatusEnum.UNKNOWN);
        taskIns.setEndTime(endTime);
        taskIns.setCostTime(endTime - taskIns.getStartTime());
        taskIns.setTaskResult(taskResult);
        super.saveTaskInstance(taskIns);
        notifyFlow(taskIns, StatusEnum.UNKNOWN, context);
    }

    private void updateTaskState(TaskInstance taskIns, TaskResult taskResult, StatusEnum targetState,
            ActorSysContext context) {
        taskIns.setState(targetState);
        taskIns.setTaskResult(taskResult);
        super.saveTaskInstance(taskIns);
        notifyFlow(taskIns, targetState, context);
    }

    private void notifyFlow(TaskInstance taskIns, StatusEnum state, ActorSysContext context) {
        FlowMsg flowMsg = FlowMsg.builder()//
                .flowInstanceId(taskIns.getFlowInstanceId())//
                //.flowId(taskIns.getFlowId())//
                .taskState(Pair.of(taskIns.getInstanceId(), state))//
                .actionType(ActionType.RUN)//
                .isManualAction(false)//
                .build();
        super.notifyFlowActor(flowMsg, context);
    }

    private void notifyNextTasks(TaskInstance taskIns, ActorSysContext context) {
        if (!taskIns.hasNextTask()) {
            return;
        }
        taskIns.getNextInstanceIds().forEach(nextInsId -> {
            TaskMsg nextTaskMsg = TaskMsg.builder()//
                    .flowInstanceId(taskIns.getFlowInstanceId())//
                    .taskInstanceId(nextInsId)//
                    .actionType(ActionType.WAIT)//
                    .isManualAction(false)//
                    .build();
            context.notify(nextInsId, nextTaskMsg);
        });
    }

    /**
     * 如果需要则发送Task业务事件.
     *
     * @param task task实例
     */
    private void sendTaskGlobalEventIfNeeded(TaskInstance task) {
        if (StrUtil.isNotEmpty(task.getEventId()) && task.eventTime() != null && task.eventSegment() != null) {
            GlobalEvent event = new GlobalEvent();
            event.setId(task.getEventId());
            event.setTaskInstanceId(task.getInstanceId());
            event.setFlowInstanceId(task.getFlowInstanceId());
            event.setType(EventTypeEnum.TASK);
            event.setEventTime(task.eventTime());
            event.setTimeSegment(task.eventSegment());
            super.eventOperator.occurredEvent(event);
        } else {
            log.warn("任务实例:taskInsId=[{}]没有设置事件相关参数,无法发送事件", task.getInstanceId());
        }
    }
}
