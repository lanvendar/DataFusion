package com.datafusion.scheduler.master.flow.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import com.datafusion.scheduler.master.task.TaskMsg;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 流程提交消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowSubmitMsgHandler extends AbstractFlowMsgHandler {

    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowSubmitMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.SUBMIT;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作只能从 INIT_SUCCESS 状态提交
        return EnumSet.of(StatusEnum.WAIT_DEPENDENT);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从 INIT_SUCCESS 提交
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.WAIT_DEPENDENT);
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext context) {
        FlowInstance flowIns = super.getInstanceById(msg.getFlowInstanceId());
        fillStartTimeIfAbsent(flowIns);
        flowIns.setState(StatusEnum.SUBMITTING);
        super.saveFlowInstance(flowIns);
        //流程依赖结束,通知任务.
        TaskMsg taskMsg = TaskMsg.builder()//
                .flowInstanceId(flowIns.getInstanceId())//
                .actionType(ActionType.WAIT)//
                .isManualAction(false)//
                .build();
        context.broadcastToChildren(taskMsg);
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext context) {
        // 更新提交中状态.
        FlowInstance flowIns = super.getInstanceById(msg.getFlowInstanceId());
        fillStartTimeIfAbsent(flowIns);
        flowIns.setState(StatusEnum.SUBMITTING);
        super.saveFlowInstance(flowIns);
        msg.setManualAction(false);
        context.notify(msg);
    }

    /**
     * 首次提交时记录流程开始时间.
     *
     * @param flowIns 流程实例
     */
    private void fillStartTimeIfAbsent(FlowInstance flowIns) {
        if (flowIns.getStartTime() == null || flowIns.getStartTime() <= 0) {
            flowIns.setStartTime(System.currentTimeMillis());
        }
    }
}
