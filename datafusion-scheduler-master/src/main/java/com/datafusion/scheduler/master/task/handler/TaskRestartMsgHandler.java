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
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 任务重启消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskRestartMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskRestartMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.RESTART;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        return EnumSet.of(StatusEnum.SUBMIT_FAILURE, StatusEnum.RUN_FAILURE);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从失败状态重启
        return EnumSet.of(StatusEnum.SUBMIT_FAILURE, StatusEnum.RUN_FAILURE, StatusEnum.STOP_SUCCESS, StatusEnum.STOP_FAILURE, StatusEnum.KILLED);

    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        Integer retryTimes = taskIns.getRetryTimes();
        Integer maxRetryTimes = taskIns.getMaxRetryTimes();

        if (retryTimes >= maxRetryTimes) {
            log.info("任务实例 [{}], state=[{}], 不符合重试条件, 当前值: {}, 最大值: {}",
                    taskIns.getInstanceId(), taskIns.getState(),
                    retryTimes, maxRetryTimes);
            // 通知流程 Actor
            Pair<String, StatusEnum> state = Pair.of(msg.getTaskInstanceId(), taskIns.getState());
            FlowMsg flowMsg = FlowMsg.builder()
                    .flowInstanceId(taskIns.getFlowInstanceId())
                    .actionType(ActionType.RUN)
                    .isManualAction(false)
                    .taskState(state)
                    .build();
            super.notifyFlowActor(flowMsg, context);
            return;
        }

        // 保存重试次数信息
        //taskIns.setState(StateEnum.RETRYING);
        taskIns.setState(StatusEnum.RESTARTING);
        taskIns.setRetryTimes(retryTimes + 1);
        saveTaskInstance(taskIns);

        log.debug("[{}] - 任务实例开始发起重试", taskIns.getInstanceId());

        // 发送重试消息
        TaskMsg retryMsg = TaskMsg.builder()
                .taskInstanceId(taskIns.getInstanceId())
                .actionType(ActionType.SUBMIT)
                .isManualAction(false)
                .build();
        context.notify(retryMsg);
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        taskIns.setState(StatusEnum.RESTARTING);
        //重置重试次数
        taskIns.setRetryTimes(0);
        taskIns.setStartTime(0L);
        taskIns.setEndTime(0L);
        taskIns.setCostTime(0L);
        super.saveTaskInstance(taskIns);

        //重新进入提交阶段
        TaskMsg submitMsg = TaskMsg.builder()//
                .taskInstanceId(taskIns.getInstanceId())//
                .actionType(ActionType.SUBMIT)//
                .isManualAction(false)//
                .build();
        context.notify(submitMsg);
        //通知流程
        FlowMsg flowMsg = FlowMsg.builder()//
                .flowInstanceId(taskIns.getFlowInstanceId())//
                //.flowId(taskIns.getFlowId())//
                .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.RESTARTING))//
                .actionType(ActionType.RUN)//
                .isManualAction(false)//
                .build();
        super.notifyFlowActor(flowMsg, context);
    }
}
