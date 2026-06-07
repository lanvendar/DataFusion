package com.datafusion.scheduler.master.task.handler;

import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 任务停止消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskStopMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskStopMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.STOP;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.INIT_FAILURE, StatusEnum.WAIT_DEPENDENT,
                StatusEnum.SUBMIT_SUCCESS, StatusEnum.SUBMIT_FAILURE, StatusEnum.RUNNING, StatusEnum.STOPPING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从多个状态停止
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.INIT_FAILURE, StatusEnum.WAIT_DEPENDENT, //StateEnum.WAIT_RESOURCES,
                StatusEnum.SUBMIT_SUCCESS, StatusEnum.SUBMIT_FAILURE, StatusEnum.RUNNING);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        if (msg.getTaskResult() == null) {
            handleRecoveryStop(taskIns, context);
            return;
        }
        //更新成[STOPPING]后统一由TaskRunMsgHandle处理
        StatusEnum acceptState = msg.getTaskResult().getTaskState();
        handleStopResult(taskIns, acceptState, context);
    }

    private void handleRecoveryStop(TaskInstance taskIns, ActorSysContext context) {
        StatusEnum state = taskIns.getState();
        if (state == StatusEnum.INIT_SUCCESS || state == StatusEnum.INIT_FAILURE || state == StatusEnum.WAIT_DEPENDENT) {
            taskIns.setState(StatusEnum.STOP_SUCCESS);
            super.saveTaskInstance(taskIns);
            notifyFlow(taskIns, StatusEnum.STOP_SUCCESS, context);
            return;
        }
        try {
            if (state != StatusEnum.STOPPING) {
                taskIns.setState(StatusEnum.STOPPING);
                super.saveTaskInstance(taskIns);
                notifyFlow(taskIns, StatusEnum.STOPPING, context);
            }
            TaskResult taskResult = super.masterTaskOperator.stopTask(taskIns);
            if (taskResult != null && taskResult.getTaskState() != null) {
                handleStopResult(taskIns, taskResult.getTaskState(), context);
            }
        } catch (Exception e) {
            taskIns.setState(StatusEnum.STOP_FAILURE);
            super.saveTaskInstance(taskIns);
            notifyFlow(taskIns, StatusEnum.STOP_FAILURE, context);
        }
    }

    private void handleStopResult(TaskInstance taskIns, StatusEnum acceptState, ActorSysContext context) {
        if (acceptState == StatusEnum.STOP_SUCCESS || acceptState == StatusEnum.STOP_FAILURE) {
            taskIns.setState(acceptState);
            super.saveTaskInstance(taskIns);
        }
        log.warn("[{}] - 任务停止结果: {}", taskIns.getInstanceId(), acceptState);
        notifyFlow(taskIns, acceptState, context);
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

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        StatusEnum state = taskIns.getState();
        //提交中[SUBMITTING] 因不确认是否与worker完成交互,故需要延迟处理
        if (state == StatusEnum.INIT_SUCCESS || state == StatusEnum.INIT_FAILURE || state == StatusEnum.WAIT_DEPENDENT) {
            //|| state == StateEnum.WAIT_RESOURCES){
            taskIns.setState(StatusEnum.STOP_SUCCESS);
            super.saveTaskInstance(taskIns);
            FlowMsg flowMsg = FlowMsg.builder()//
                    .flowInstanceId(taskIns.getFlowInstanceId())//
                    //.flowId(taskIns.getFlowId())//
                    .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.STOP_SUCCESS))//
                    .actionType(ActionType.RUN)//
                    .isManualAction(false)//
                    .build();
            super.notifyFlowActor(flowMsg, context);
            return;
        }

        if (state == StatusEnum.SUBMIT_SUCCESS || state == StatusEnum.SUBMIT_FAILURE || state == StatusEnum.RUNNING) {
            try {
                taskIns.setState(StatusEnum.STOPPING);
                super.saveTaskInstance(taskIns);
                FlowMsg flowMsg = FlowMsg.builder()//
                        .flowInstanceId(taskIns.getFlowInstanceId())//
                        //.flowId(taskIns.getFlowId())//
                        .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.STOPPING))//
                        .actionType(ActionType.RUN)//
                        .isManualAction(false)//
                        .build();
                super.notifyFlowActor(flowMsg, context);
                TaskResult taskResult = super.masterTaskOperator.stopTask(taskIns);
                if (taskResult != null && taskResult.getTaskState() != null) {
                    handleStopResult(taskIns, taskResult.getTaskState(), context);
                }
            } catch (Exception e) {
                taskIns.setState(StatusEnum.STOP_FAILURE);
                super.saveTaskInstance(taskIns);
                FlowMsg flowMsg = FlowMsg.builder()//
                        .flowInstanceId(taskIns.getFlowInstanceId())//
                        //.flowId(taskIns.getFlowId())//
                        .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.STOP_FAILURE))//
                        .actionType(ActionType.RUN)//
                        .isManualAction(false)//
                        .build();
                super.notifyFlowActor(flowMsg, context);
                //TODO 是否需要自动进入强制停止?
            }
        }
    }
}
