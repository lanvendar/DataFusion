package com.datafusion.scheduler.master.flow.handler;

import cn.hutool.core.util.StrUtil;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.actor.enums.ActorStopReason;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

import static com.datafusion.scheduler.enums.StatusEnum.*;

/**
 * 流程运行消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowRunMsgHandler extends AbstractFlowMsgHandler {

    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowRunMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.RUN;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        //此处就是流程提交后的全状态.
        return EnumSet.of(SUBMITTING, RUNNING, STOPPING, STOP_SUCCESS, RUN_SUCCESS, RUN_FAILURE);
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext context) {
        FlowInstance flowIns = super.getInstanceById(msg.getFlowInstanceId());

        // flowState 由 FlowActor 计算后设入 msg
        StatusEnum flowState = msg.getFlowTargetState();
        if (flowState != null) {
            if (flowState != flowIns.getState()) {
                flowIns.setState(flowState);
                super.saveFlowInstance(flowIns);
            }
        } else {
            //更新流程最终状态,并清理流程 flow actor
            sendGlobalEventIfNeeded(flowIns);
            flowIns.setState(RUN_SUCCESS);
            flowIns.setEndTime(System.currentTimeMillis());
            super.saveFlowInstance(flowIns);
            //正常停止,销毁流程 actor
            context.getSelf().destroy(ActorStopReason.STOPPED, null);
        }
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext context) {
        log.error("不可能发生!!!程序异常!!!");
    }

    /**
     * 如果需要则发送flow全局事件.
     *
     * @param flowIns flow实例
     */
    private void sendGlobalEventIfNeeded(FlowInstance flowIns) {
        if (StrUtil.isNotEmpty(flowIns.getEventId()) && flowIns.eventTime() != null && flowIns.eventSegment() != null) {
            GlobalEvent event = new GlobalEvent();
            event.setId(flowIns.getEventId());
            event.setFlowInstanceId(flowIns.getInstanceId());
            event.setType(EventTypeEnum.FLOW);
            event.setEventTime(flowIns.eventTime());
            event.setTimeSegment(flowIns.eventSegment());
            super.eventOperator.occurredEvent(event);
        } else {
            log.warn("流程实例:flowInsId=[{}]没有设置事件相关参数,无法发送事件", flowIns.getInstanceId());
        }
    }
}
