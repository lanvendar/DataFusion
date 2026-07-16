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
        // 手工动作可以从 STOP_FAILURE/UNKNOWN Kill
        return EnumSet.of(StatusEnum.STOP_FAILURE, StatusEnum.UNKNOWN);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        if (msg.getTaskResult() == null) {
            handleRecoveryKill(taskIns, context);
            return;
        }
        //处理worker结果消息
        TaskResult acceptState = msg.getTaskResult();
        if (null != acceptState) {
            if (StatusEnum.KILLED == acceptState.getTaskState()) {
                taskIns.setState(StatusEnum.KILLED);
                taskIns.setTaskResult(acceptState);
                super.saveTaskInstance(taskIns);
                notifyFlow(taskIns, StatusEnum.KILLED, context, false);
                return;
            }
        }
        log.error("不可能发生!!!程序异常!!!");
    }

    private void handleRecoveryKill(TaskInstance taskIns, ActorSysContext context) {
        StatusEnum finalState;
        TaskResult taskResult = null;
        try {
            taskResult = super.getMasterTaskOperator().killTask(taskIns);
            finalState = resolveRecoveryKillState(taskResult);
        } catch (Exception e) {
            log.error("[{}] - 任务实例恢复强杀失败.", taskIns.getInstanceId(), e);
            finalState = StatusEnum.UNKNOWN;
        }
        taskIns.setState(finalState);
        if (taskResult != null) {
            taskIns.setTaskResult(taskResult);
        }
        super.saveTaskInstance(taskIns);
        notifyFlow(taskIns, finalState, context, false);
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        taskIns.setState(StatusEnum.KILLING);
        super.saveTaskInstance(taskIns);
        notifyFlow(taskIns, StatusEnum.KILLING, context, true);

        StatusEnum finalState;
        TaskResult taskResult = null;
        try {
            taskResult = super.getMasterTaskOperator().killTask(taskIns);
            finalState = resolveKillState(taskResult);
        } catch (Exception e) {
            log.error("[{}] - 任务实例强杀失败.", taskIns.getInstanceId(), e);
            finalState = StatusEnum.UNKNOWN;
        }
        taskIns.setState(finalState);
        if (taskResult != null) {
            taskIns.setTaskResult(taskResult);
        }
        super.saveTaskInstance(taskIns);
        notifyFlow(taskIns, finalState, context, true);
    }

    private StatusEnum resolveKillState(TaskResult taskResult) {
        if (taskResult == null || taskResult.getTaskState() == null) {
            return StatusEnum.UNKNOWN;
        }
        return taskResult.getTaskState();
    }

    private StatusEnum resolveRecoveryKillState(TaskResult taskResult) {
        if (taskResult == null || taskResult.getTaskState() == null) {
            return StatusEnum.KILLING;
        }
        return taskResult.getTaskState();
    }

    private void notifyFlow(TaskInstance taskIns, StatusEnum finalState, ActorSysContext context, boolean manualAction) {
        FlowMsg flowMsg = FlowMsg.builder()
                .flowInstanceId(taskIns.getFlowInstanceId())
                .taskState(Pair.of(taskIns.getInstanceId(), finalState))
                .actionType(ActionType.RUN)
                .isManualAction(manualAction)
                .build();
        super.notifyFlowActor(flowMsg, context);
    }
}
