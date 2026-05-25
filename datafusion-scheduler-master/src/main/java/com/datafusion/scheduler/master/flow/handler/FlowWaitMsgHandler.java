package com.datafusion.scheduler.master.flow.handler;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventListener;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 流程等待消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowWaitMsgHandler extends AbstractFlowMsgHandler {

    /**
     * 构造函数.
     *
     * @param flowStorage   流程存储
     * @param eventOperator 全局事件操作
     */
    public FlowWaitMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        super(flowStorage, eventOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.WAIT;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        return EnumSet.of(StatusEnum.INIT_SUCCESS);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        //不支持手动触发
        return null;
    }

    @Override
    protected void handleAction(FlowMsg msg, ActorSysContext context) {
        FlowInstance flowIns = super.getInstanceById(msg.getFlowInstanceId());
        // 更新等待依赖状态.
        StatusEnum state = flowIns.getState();
        if (StatusEnum.INIT_SUCCESS == state) {
            flowIns.setState(StatusEnum.WAIT_DEPENDENT);
            super.saveFlowInstance(flowIns);
        }

        //依赖不满足,等待依赖
        if (!checkGlobalEvent(flowIns, msg, context)) {
            return;
        }

        //进入提交阶段,流程没有资源等待,故发送FlowSubmitMsg消息
        FlowMsg submitMsg = FlowMsg.builder()//
                .actionType(ActionType.SUBMIT)//
                .isManualAction(false)//
                .flowInstanceId(flowIns.getInstanceId())//
                .build();
        //发送提交消息
        context.notify(submitMsg);
    }

    @Override
    protected void handleManualAction(FlowMsg msg, ActorSysContext context) {
        log.error("不可能发生!!!程序异常!!!");
    }

    /**
     * 检查任务实例全局依赖.
     *
     * @param flowIns 流程实例
     * @param msg     消息
     * @param context actor 上下文
     * @return 检查加过
     */
    private boolean checkGlobalEvent(FlowInstance flowIns, FlowMsg msg, ActorSysContext context) {
        if (flowIns.eventTime() == null || flowIns.eventTime() <= 0 || CollectionUtil.isEmpty(
                flowIns.getDepEventIds())) {
            log.debug("流程检查全局事件条件不满足，跳过检查");
            return true;
        }

        for (String eventId : flowIns.getDepEventIds()) {
            Pair<String, Long> eventKey = Pair.of(eventId, flowIns.eventTime());
            if (!super.eventOperator.checkEvents(eventKey, flowIns.eventTime())) {
                log.debug("事件还未发生,eventKey = {} ", eventKey);
                GlobalEventListener listener = (GlobalEvent event) -> {
                    log.debug("事件已发生,通知自身重新处理FlOW_WAIT消息");
                    context.notify(msg);
                };
                super.eventOperator.registerListener(eventKey, listener);
                return false;
            }
        }
        return true;
    }
}
