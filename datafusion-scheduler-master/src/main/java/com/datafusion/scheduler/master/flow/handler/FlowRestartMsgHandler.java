package com.datafusion.scheduler.master.flow.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 流程重启消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowRestartMsgHandler extends AbstractFlowMsgHandler {
    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowRestartMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.RESTART;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作不支持重启
        return null;
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从失败状态重启
        return EnumSet.of(StatusEnum.RUN_FAILURE, StatusEnum.STOP_SUCCESS, StatusEnum.STOP_FAILURE);
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext context) {
        // 自动动作不支持
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext context) {
        FlowInstance flowIns = getInstanceById(msg.getFlowInstanceId());
        log.debug("FlowRestartMsgHandler handleManualAction: {}", msg.getFlowInstanceId());

        // 重置实例时间
        flowIns.setStartTime(null);
        flowIns.setEndTime(null);

        // 变为重启中
        flowIns.setState(StatusEnum.RESTARTING);
        saveFlowInstance(flowIns);

        // 变为提交中
        flowIns.setState(StatusEnum.SUBMITTING);
        saveFlowInstance(flowIns);

        log.info("Flow[{}] restarting", flowIns.getInstanceId());
    }
}
