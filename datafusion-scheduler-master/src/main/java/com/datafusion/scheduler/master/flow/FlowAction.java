package com.datafusion.scheduler.master.flow;

import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterStorage;
import com.datafusion.scheduler.master.actor.Actor;
import com.datafusion.scheduler.master.actor.ActorProxy;
import com.datafusion.scheduler.master.actor.ActorSystem;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.handler.FlowEnforceSuccessMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowInitMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowMsgHandlerRegister;
import com.datafusion.scheduler.master.flow.handler.FlowRestartMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowRunMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowStopMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowSubmitMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowWaitMsgHandler;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import com.datafusion.scheduler.master.task.TaskAction;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.trigger.SchedulerTrigger;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 流程动作处理类.
 *
 * <p>
 * 1. 实现自动调度触发器接口
 * 2. 实现用户手动指令操作
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/28
 * @since 2026/2/28
 */
@Slf4j
public class FlowAction implements SchedulerTrigger {
    /**
     * 创建 Actor 系统.
     */
    private final ActorSystem actorSystem;

    /**
     * 创建流程存储.
     */
    private final FlowMsgHandlerRegister msgHandlerRegister;

    /**
     * 综合存储.
     */
    private final MasterStorage masterStorage;

    /**
     * 全局事件操作类.
     *
     */
    private final GlobalEventOperator eventOperator;

    /**
     * 创建任务动作处理类.
     */
    @Setter
    private TaskAction taskAction;

    /**
     * 构造函数.
     *
     * @param actorSystem   Actor 系统
     * @param eventOperator 全局事件操作类
     * @param masterStorage 存储
     *
     */
    public FlowAction(ActorSystem actorSystem, GlobalEventOperator eventOperator, MasterStorage masterStorage) {
        this.actorSystem = actorSystem;
        this.eventOperator = eventOperator;
        this.masterStorage = masterStorage;
        this.msgHandlerRegister = initFlowMsgHandlerRegister();
    }

    /**
     * 初始化流程消息处理器注册.
     *
     * @return 流程消息处理器注册
     */
    private FlowMsgHandlerRegister initFlowMsgHandlerRegister() {
        FlowStorage flowStorage = masterStorage.getFlowStorage();
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        register.registerHandler(new FlowInitMsgHandler(flowStorage, eventOperator));
        register.registerHandler(new FlowWaitMsgHandler(flowStorage, eventOperator));
        register.registerHandler(new FlowSubmitMsgHandler(flowStorage, eventOperator));
        register.registerHandler(new FlowRunMsgHandler(flowStorage, eventOperator));
        register.registerHandler(new FlowStopMsgHandler(flowStorage, eventOperator));
        register.registerHandler(new FlowRestartMsgHandler(flowStorage, eventOperator));
        register.registerHandler(new FlowEnforceSuccessMsgHandler(flowStorage, eventOperator));
        return register;
    }

    /**
     * 流程初始化动作.
     *
     * @param triggerInstance 调度器缓存实例
     */
    @Override
    public void fetchInit(TriggerInstance triggerInstance) {
        //当前先不走 actor,直接创建 instance 实例的记录, 到dispatchSubmit中才创建 actor
        FlowMsg msg = FlowMsg.builder()//
                .flowId(triggerInstance.getPayloadId())//
                .version(triggerInstance.getVersion())//
                .scheduleTime(triggerInstance.getScheduleTime())//
                .flowInstanceId(triggerInstance.getInstanceId())//
                .actionType(ActionType.INIT)//
                .isManualAction(false)//
                .build();

        FlowMsgHandler handler = msgHandlerRegister.getHandler(ActionType.INIT);
        if (handler != null) {
            handler.handle(msg, null);
        }

        // 创建任务实例
        taskAction.fetchInit(triggerInstance);

        // TODO 后续也可以在此处就创建 actor:
        //  1.提早创建actor进行依赖的处理,但是需要考虑整体性能内存的扩大.
        //  2.同时需要考虑actor的异步处理消息的同步性.
    }

    @Override
    public void dispatchSubmit(TriggerInstance triggerInstance) {
        FlowMsg msg = FlowMsg.builder()
                .flowInstanceId(triggerInstance.getInstanceId())
                .actionType(ActionType.WAIT)
                .isManualAction(false)
                .build();
        // 创建 FlowActor 并发送消息
        ActorProxy actor = createFlowActor(triggerInstance.getInstanceId());
        // 第一次发送消息
        actor.notify(msg);
        // 创建 TaskActor 并发送消息
        taskAction.dispatchSubmit(triggerInstance);
    }

    @Override
    public void killDelay(String payloadId, long delayTime) {

    }

    /**
     * 恢复未完成流程实例的 Actor 运行态.
     */
    public void reloadFlows() {
        List<FlowInstance> flowInstances = masterStorage.getFlowStorage().getAvailableInstance(null);
        for (FlowInstance flowInstance : flowInstances) {
            if (flowInstance == null) {
                continue;
            }
            StatusEnum flowState = flowInstance.getState();
            if (flowState != null && flowState.isSuccess()) {
                continue;
            }
            try {
                reloadFlow(flowInstance);
            } catch (Exception e) {
                log.warn("恢复流程实例运行态失败,flowInstanceId={}",
                        flowInstance == null ? null : flowInstance.getInstanceId(), e);
            }
        }
        log.info("恢复流程运行态数量: {}", flowInstances.size());
    }

    private void reloadFlow(FlowInstance flowInstance) {
        if (flowInstance.getState() == StatusEnum.INITIALIZING) {
            fetchInit(toTriggerInstance(flowInstance));
            FlowInstance initialized = masterStorage.getFlowStorage().getInstanceById(flowInstance.getInstanceId());
            if (initialized != null && initialized.getState() != StatusEnum.INITIALIZING) {
                reloadFlow(initialized);
            }
            return;
        }
        ActorProxy flowActor = createFlowActor(flowInstance.getInstanceId());
        restoreTaskState(flowActor, flowInstance.getInstanceId());
        taskAction.reloadTasks(flowInstance.getInstanceId());
        recoverFlow(flowActor, flowInstance);
    }

    private TriggerInstance toTriggerInstance(FlowInstance flowInstance) {
        TriggerInstance triggerInstance = new TriggerInstance();
        triggerInstance.setPayloadId(flowInstance.getFlowId());
        triggerInstance.setVersion(flowInstance.getVersion());
        triggerInstance.setScheduleTime(flowInstance.getScheduleTime());
        triggerInstance.setInstanceId(flowInstance.getInstanceId());
        triggerInstance.setState(flowInstance.getState());
        return triggerInstance;
    }

    private void restoreTaskState(ActorProxy flowActor, String flowInstanceId) {
        List<TaskInstance> taskInstances = masterStorage.getTaskStorage().getTaskInsIdsByFlowInsId(flowInstanceId);
        for (TaskInstance taskInstance : taskInstances) {
            if (taskInstance == null || taskInstance.getState() == null) {
                continue;
            }
            FlowMsg msg = FlowMsg.builder()
                    .flowInstanceId(flowInstanceId)
                    .actionType(ActionType.RUN)
                    .isManualAction(false)
                    .restoreTaskState(true)
                    .taskState(Pair.of(taskInstance.getInstanceId(), taskInstance.getState()))
                    .build();
            flowActor.notify(msg);
        }
    }

    private void recoverFlow(ActorProxy flowActor, FlowInstance flowInstance) {
        ActionType actionType = getRecoverAction(flowInstance.getState());
        if (actionType == null) {
            return;
        }
        FlowMsg msg = FlowMsg.builder()
                .flowInstanceId(flowInstance.getInstanceId())
                .flowId(flowInstance.getFlowId())
                .version(flowInstance.getVersion())
                .scheduleTime(flowInstance.getScheduleTime())
                .actionType(actionType)
                .isManualAction(false)
                .build();
        flowActor.notify(msg);
    }

    private ActionType getRecoverAction(StatusEnum state) {
        if (state == null) {
            return null;
        }
        switch (state) {
            case INIT_SUCCESS:
            case WAIT_DEPENDENT:
                return ActionType.WAIT;
            case STOPPING:
                return ActionType.STOP;
            default:
                return null;
        }
    }

    /**
     * 创建 FlowActor.
     *
     * @param flowInstanceId : 流程实例ID
     * @return ActorProxy
     */
    private ActorProxy createFlowActor(String flowInstanceId) {
        // 创建 FlowActor
        return actorSystem.createParentActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return flowInstanceId;
            }

            @Override
            public Actor createActor() {
                return new FlowActor(flowInstanceId, msgHandlerRegister);
            }
        });
    }

    //region 用户手动指令操作
    /**
     * 重新初始化.
     *
     * @param instance  流程实例
     */
    public void flowReInit(FlowInstance instance) {
        //当前先不走 actor,直接创建 instance 实例的记录, 到dispatchSubmit中才创建 actor
        FlowMsg msg = FlowMsg.builder()//
                .flowId(instance.getFlowId())//
                .version(instance.getVersion())//
                .scheduleTime(instance.getScheduleTime())//
                .flowInstanceId(instance.getInstanceId())//
                .actionType(ActionType.INIT)//
                .isManualAction(true)//
                .build();

        FlowMsgHandler handler = msgHandlerRegister.getHandler(ActionType.INIT);
        if (handler != null) {
            handler.handle(msg, null);
        }

        // 创建任务实例
        taskAction.taskReInit(instance);
    }

    /**
     * 立即提交流程(忽略依赖提交).
     *
     * @param instance 流程实例
     */
    public void flowSubmit(FlowInstance instance) {
        this.manualAction(instance, ActionType.SUBMIT);
    }

    /**
     * 停止流程.
     *
     * @param instance 流程实例
     */
    public void flowStop(FlowInstance instance) {
        this.manualAction(instance, ActionType.STOP);
    }
    // TODO 重新运行流程,待实现
    // TODO 强制成功流程,待实现

    /**
     * 手动触发流程动作.
     *
     * @param instance   流程实例
     * @param actionType 动作
     */
    private void manualAction(FlowInstance instance, ActionType actionType) {
        FlowMsg msg = FlowMsg.builder()
                .flowInstanceId(instance.getInstanceId())
                .actionType(actionType)
                .isManualAction(true)
                .build();
        String actorId = instance.getInstanceId();
        actorSystem.notify(actorId, msg);
    }
    //endregion
}
