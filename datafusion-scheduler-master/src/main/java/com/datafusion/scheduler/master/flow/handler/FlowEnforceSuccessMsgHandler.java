package com.datafusion.scheduler.master.flow.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 流程强制成功消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowEnforceSuccessMsgHandler extends AbstractFlowMsgHandler {
    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowEnforceSuccessMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.ENFORCE_SUCCESS;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作不支持强制成功
        return null;
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 自动动作不支持强制成功
        return null;
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext context) {
        // TODO,目前在RUN中处理
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext context) {
        // TODO,目前在RUN中处理
    }
}
