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
import com.datafusion.scheduler.master.task.TaskExecutor;
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
     * @param taskExecutor  任务执行器
     */
    public TaskRunMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, TaskExecutor taskExecutor) {
        super(taskStorage, eventOperator, taskExecutor);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.RUN;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作可以从 SUBMITTING, RUNNING 状态
        return EnumSet.of(StatusEnum.SUBMITTING, StatusEnum.SUBMIT_FAILURE, StatusEnum.SUBMIT_SUCCESS, StatusEnum.RUNNING, StatusEnum.STOPPING);

    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        return null;
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = super.getTaskInstance(msg.getTaskInstanceId());
        StatusEnum currentState = taskIns.getState();
        //只要收到消息，就认为任务已经运行了
        if (getPreState().contains(currentState)) {
            taskIns.setState(StatusEnum.RUNNING);
            taskIns.setStartTime(System.currentTimeMillis());
            super.saveTaskInstance(taskIns);
            FlowMsg flowMsg = FlowMsg.builder()//
                    .flowInstanceId(taskIns.getFlowInstanceId())//
                    //.flowId(taskIns.getFlowId())//
                    .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.RUNNING))//
                    .actionType(ActionType.RUN)//
                    .isManualAction(false)//
                    .build();
            super.notifyFlowActor(flowMsg, context);
        }

        //处理结果消息
        TaskResult acceptState = msg.getTaskResult();
        if (null != acceptState) {
            //任务运行中不更新,维持状态
            if (acceptState.getTaskState() == StatusEnum.RUNNING) {
                return;
            }
            //任务成功处理
            if (acceptState.getTaskState() == StatusEnum.RUN_SUCCESS) {
                try {
                    super.taskExecutor.finishTask(taskIns);
                } catch (Exception e) {
                    log.error("任务实例无法完成:taskInsId=[{}]", taskIns.getInstanceId());
                    //TODO finish接口失败怎么办？
                }
                taskIns.setState(StatusEnum.RUN_SUCCESS);
                taskIns.setEndTime(System.currentTimeMillis());
                taskIns.setCostTime(taskIns.getEndTime() - taskIns.getStartTime());
                taskIns.setTaskResult(acceptState);
                //发送事件
                sendTaskGlobalEventIfNeeded(taskIns);
                super.saveTaskInstance(taskIns);
                FlowMsg flowMsg = FlowMsg.builder()//
                        .flowInstanceId(taskIns.getFlowInstanceId())//
                        //.flowId(taskIns.getFlowId())//
                        .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.RUN_SUCCESS))//
                        .actionType(ActionType.RUN)//
                        .isManualAction(false)//
                        .build();
                super.notifyFlowActor(flowMsg, context);
                //通知下一个任务,TODO 是否由流程收到成功消息后,由流程触发?
                if (taskIns.hasNextTask()) {
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
                return;
            }
            //任务失败处理
            if (acceptState.getTaskState() == StatusEnum.RUN_FAILURE) {
                taskIns.setEndTime(System.currentTimeMillis());
                taskIns.setCostTime(taskIns.getEndTime() - taskIns.getStartTime());
                taskIns.setState(StatusEnum.RUN_FAILURE);
                taskIns.setTaskResult(acceptState);
                super.saveTaskInstance(taskIns);
                TaskMsg taskMsg = TaskMsg.builder()//
                        .flowInstanceId(taskIns.getFlowInstanceId())//
                        .taskInstanceId(taskIns.getInstanceId())//
                        .actionType(ActionType.RESTART)//
                        .isManualAction(false)//
                        .build();
                context.notify(taskMsg);
            }
        }
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        log.error("不可能发生!!!程序异常!!!");
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
