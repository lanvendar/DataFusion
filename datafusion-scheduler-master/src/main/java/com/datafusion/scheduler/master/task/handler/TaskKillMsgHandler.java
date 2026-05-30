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
import com.datafusion.scheduler.model.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;

/**
 * 任务 Kill 消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskKillMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskKillMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.KILL;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作不支持 Kill
        return EnumSet.of(StatusEnum.KILLING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从 STOP_FAILURE Kill
        return EnumSet.of(StatusEnum.STOP_FAILURE);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        //处理worker结果消息
        TaskResult acceptState = taskIns.getTaskResult();
        if (null != acceptState) {
            if (StatusEnum.KILLED == acceptState.getTaskState()) {
                taskIns.setState(StatusEnum.KILLED);
                super.saveTaskInstance(taskIns);
                FlowMsg flowMsg = FlowMsg.builder()
                        .flowInstanceId(taskIns.getFlowInstanceId())
                        //.flowId(taskIns.getFlowId())
                        .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.KILLED))
                        .actionType(ActionType.RUN)
                        .isManualAction(false)
                        .build();
                super.notifyFlowActor(flowMsg, context);
                return;
            }
        }
        log.error("不可能发生!!!程序异常!!!");
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        StatusEnum finalState;
        try {
            finalState = StatusEnum.KILLING;
            super.getMasterTaskOperator().killTask(taskIns);
        } catch (Exception e) {
            String taskInsId = taskIns.getTaskId();
            log.error("[{}] - 任务实例强杀失败.", taskInsId);
            // 异常则直接从 STOP_FAILURE -> UNKNOWN.
            finalState = StatusEnum.UNKNOWN;
        }
        taskIns.setState(finalState);
        super.saveTaskInstance(taskIns);
        FlowMsg flowMsg = FlowMsg.builder()
                .flowInstanceId(taskIns.getFlowInstanceId())
                //.flowId(taskIns.getFlowId())
                .taskState(Pair.of(taskIns.getInstanceId(), finalState))
                .actionType(ActionType.RUN)
                .isManualAction(true)
                .build();
        super.notifyFlowActor(flowMsg, context);
    }
}
