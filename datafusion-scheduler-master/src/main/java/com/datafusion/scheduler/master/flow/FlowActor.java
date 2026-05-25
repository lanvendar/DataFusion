package com.datafusion.scheduler.master.flow;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.ActorType;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.actor.core.AbstractActor;
import com.datafusion.scheduler.master.actor.enums.ActorStopReason;
import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;
import com.datafusion.scheduler.master.flow.handler.FlowMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowMsgHandlerRegister;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.datafusion.scheduler.enums.StatusEnum.*;

/**
 * 流程 Actor.
 * 1. 实现流程和任务的消息转换
 * 2. 并调用对应的执行动作
 * 3. 管理流程下所有任务状态的缓存信息
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowActor extends AbstractActor {

    /**
     * ActorId (flowInstanceId).
     */
    private final String actorId;

    /**
     * 任务 Actor 的状态缓存.
     * Key: taskInstanceId, Value: StateEnum
     */
    private final ConcurrentHashMap<String, StatusEnum> taskStateMap = new ConcurrentHashMap<>();

    /**
     * 消息处理器上下文.
     */
    private final FlowMsgHandlerRegister msgHandlerRegister;

    /**
     * 构造函数.
     *
     * @param flowInstanceId     流程实例ID (作为 ActorId)
     * @param msgHandlerRegister 消息处理器上下文
     */
    public FlowActor(String flowInstanceId, FlowMsgHandlerRegister msgHandlerRegister) {
        this.actorId = flowInstanceId;
        this.msgHandlerRegister = msgHandlerRegister;
    }

    @Override
    public String getActorId() {
        return actorId;
    }

    @Override
    public ActorTypeEnum type() {
        return ActorType.FLOW;
    }

    @Override
    public void process(ActorMsg msg) {
        if (!(msg instanceof FlowMsg)) {
            log.warn("Invalid message type: {}", msg.getClass().getName());
            return;
        }

        FlowMsg flowMsg = (FlowMsg) msg;
        log.debug("FlowActor[{}] process action: {}", actorId, flowMsg.getActionType());

        // 若为流程运行动作，则更新任务状态缓存并计算流程状态
        if (flowMsg.getActionType() == ActionType.RUN && flowMsg.getTaskState() != null) {
            StatusEnum flowState = updateTaskState(flowMsg.getTaskState().getKey(), flowMsg.getTaskState().getValue());
            flowMsg.setFlowTargetState(flowState);
        }

        FlowMsgHandler handler = msgHandlerRegister.getHandler(flowMsg.getActionType());
        if (handler != null) {
            handler.handle(flowMsg, actorSysContext);
        } else {
            log.warn("No handler found for action type: {}", flowMsg.getActionType());
        }
    }

    /**
     * 更新任务状态并计算流程状态.
     *
     * @param taskInstanceId 任务实例ID
     * @param state          任务状态
     * @return 聚合后的流程状态
     */
    public StatusEnum updateTaskState(String taskInstanceId, StatusEnum state) {
        taskStateMap.put(taskInstanceId, state);
        return computeFlowState();
    }

    /**
     * 流程状态统计.
     *
     * <p>
     * 1.流程提交前主动变化状态[INITIALIZING|INIT_SUCCESS|INIT_FAILURE|WAIT_DEPENDENT|WAIT_RESOURCES].
     * 2.流程提交后被动变化状态[SUBMITTING|RUNNING|STOPPING|STOP_SUCCESS|RUN_SUCCESS|RUN_FAILURE]统计.
     * 3.注:使用任务状态计数来判断流程状态
     *
     * @return 流程状态
     */
    private StatusEnum computeFlowState() {
        Map<StatusEnum, Integer> stateCountMap = new HashMap<>();
        // 遍历 taskActorMap 并统计状态数量
        taskStateMap.forEach((taskId, state) -> stateCountMap.compute(state, (k, v) -> v == null ? 1 : v + 1));
        // 计算各种状态的数量
        int waitCount = stateCountMap.getOrDefault(WAIT_DEPENDENT, 0) + stateCountMap.getOrDefault(SUBMITTING, 0);
        int runningCount = stateCountMap.getOrDefault(SUBMIT_SUCCESS, 0) + stateCountMap.getOrDefault(RESTARTING, 0)
                + stateCountMap.getOrDefault(RUNNING, 0) + stateCountMap.getOrDefault(STOPPING, 0)
                + stateCountMap.getOrDefault(KILLING, 0) + stateCountMap.getOrDefault(ENFORCING_SUCCESS, 0);
        int successCount = stateCountMap.getOrDefault(RUN_SUCCESS, 0) + stateCountMap.getOrDefault(ENFORCE_SUCCESS, 0);
        int failureCount = stateCountMap.getOrDefault(SUBMIT_FAILURE, 0) + stateCountMap.getOrDefault(RUN_FAILURE, 0)
                + stateCountMap.getOrDefault(STOP_FAILURE, 0);
        int stoppedCount = stateCountMap.getOrDefault(STOP_SUCCESS, 0) + stateCountMap.getOrDefault(KILLED, 0);
        //最终状态计数
        int finalCount = successCount + failureCount + stoppedCount;
        // 任务未进入最终状态
        if (finalCount < taskStateMap.size()) {
            if (waitCount + finalCount == taskStateMap.size()) {
                return SUBMITTING;
            }
            //兜底状态
            if (runningCount > 0) {
                return RUNNING;
            }
        }
        // 任务都进入最终状态
        if (finalCount == taskStateMap.size()) {
            if (successCount == taskStateMap.size()) {
                return RUN_SUCCESS;
            }
            if (stoppedCount + successCount == taskStateMap.size()) {
                return STOP_SUCCESS;
            }
            //兜底状态,等价条件(failureCount+stoppedCount+successCount=taskStateMap.size())
            if (failureCount > 0) {
                return RUN_FAILURE;
            }
        }
        return UNKNOWN;
    }

    @Override
    public void init(ActorSysContext actorSysContext) {
        super.init(actorSysContext);
        log.debug("FlowActor[{}] init", actorId);
    }

    @Override
    public void destroy(ActorStopReason stopReason, Throwable cause) {
        log.debug("FlowActor[{}] destroy, reason: {}", actorId, stopReason);
        taskStateMap.clear();
    }
}
