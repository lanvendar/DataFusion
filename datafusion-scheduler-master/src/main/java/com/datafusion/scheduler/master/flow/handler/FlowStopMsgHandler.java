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
 * 流程停止消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowStopMsgHandler extends AbstractFlowMsgHandler {
    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowStopMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.STOP;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        return EnumSet.of(StatusEnum.STOPPING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        return EnumSet.of(StatusEnum.RUNNING);
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext  context) {
        FlowInstance flowIns = getInstanceById(msg.getFlowInstanceId());
        context.broadcastToChildren(TaskMsg.builder()//
                .flowInstanceId(flowIns.getInstanceId())//
                .actionType(ActionType.STOP)//
                .isManualAction(false)//
                .build());
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext  context) {
        FlowInstance flowIns = getInstanceById(msg.getFlowInstanceId());
        flowIns.setState(StatusEnum.STOPPING);
        super.saveFlowInstance(flowIns);
        context.broadcastToChildren(TaskMsg.builder()//
                .flowInstanceId(flowIns.getInstanceId())//
                .actionType(ActionType.STOP)//
                .isManualAction(true)//
                .build());
    }
}
