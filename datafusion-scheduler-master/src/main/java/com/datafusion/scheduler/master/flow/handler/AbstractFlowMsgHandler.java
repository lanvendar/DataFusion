package com.datafusion.scheduler.master.flow.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 流程动作处理抽象类.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
@Getter
public abstract class AbstractFlowMsgHandler implements FlowMsgHandler {

    /**
     * 流程存储.
     */
    protected final FlowStorage flowStorage;

    /**
     * 全局事件操作.
     */
    protected final GlobalEventOperator eventOperator;

    /**
     * 构造函数.
     *
     * @param flowStorage 流程存储
     */
    protected AbstractFlowMsgHandler(FlowStorage flowStorage, GlobalEventOperator eventOperator) {
        this.flowStorage = flowStorage;
        this.eventOperator = eventOperator;
    }

    @Override
    public void handle(FlowMsg msg, ActorSysContext context) {
        log.debug("收到流程消息:msg=[{}]", msg);
        String flowInsId = msg.getFlowInstanceId();
        FlowInstance flowIns = getInstanceById(flowInsId);
        if (flowIns == null) {
            log.info("流程实例不存在,初始化流程实例:flowInsId=[{}]", flowInsId);
            //注意:只有初始化init的action,且实例未生成的时候走此逻辑,即第一次初始化
            if (msg.getMsgType().equals(ActionType.INIT.flowType())) {
                this.handleAction(msg, null);
            }
            return;
        }

        StatusEnum currentState = flowIns.getState();
        log.info("[{}] - 流程实例,当前流程状态:{}", flowIns.getInstanceId(), currentState);
        Set<StatusEnum> checkState = msg.isManualAction() ? getManualPreState() : getPreState();
        if (null == checkState) {
            log.error("不支持触发流程动作:动作类型是否手工=[{}],flowInsId=[{}],当前状态flowState=[{}]", msg.isManualAction(), flowInsId, currentState);
        } else if (checkState.contains(currentState)) {
            log.info("触发流程动作:动作类型是否手工=[{}],flowInsId=[{}],当前状态flowState=[{}]", msg.isManualAction(), flowInsId, currentState);
            if (msg.isManualAction()) {
                this.handleManualAction(msg, context);
            } else {
                this.handleAction(msg, context);
            }
        } else {
            log.warn("无法执行[{}]动作,flowInsId=[{}],当前状态flowState=[{}]", msg.getMsgType(), flowInsId, currentState);
        }
    }

    /**
     * 执行动作.
     *
     * @param msg             消息
     * @param actorSysContext Actor 系统上下文
     */
    protected abstract void handleAction(FlowMsg msg, ActorSysContext actorSysContext);

    /**
     * 处理手工动作.
     *
     * @param msg 消息
     * @param actorSysContext Actor 系统上下文
     */
    protected abstract void handleManualAction(FlowMsg msg, ActorSysContext actorSysContext);

    //region 流程存储相关操作
    /**
     * 获取流程实例.
     *
     * @param flowInsId 流程实例id
     * @return 流程实例
     */
    protected FlowInstance getInstanceById(String flowInsId) {
        return flowStorage.getInstanceById(flowInsId);
    }

    /**
     * 删除流程实例.
     *
     * @param flowInsId 流程实例id
     */
    protected void removeInstance(String flowInsId) {
        flowStorage.removeInstanceById(flowInsId);
    }

    /**
     * 获取流程信息.
     *
     * @param flowId 流程id
     * @return 流程信息
     */
    protected FlowInfo getFlowInfo(String flowId) {
        return flowStorage.getFlowInfo(flowId);
    }

    /**
     * 保存流程实例.
     *
     * @param flowIns 流程实例
     */
    protected void saveFlowInstance(FlowInstance flowIns) {
        flowStorage.saveInstance(flowIns);
    }
    //endregion
}
