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
 * 任务强制成功消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskEnforceSuccessMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskEnforceSuccessMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.ENFORCE_SUCCESS;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        //自动流转前置状态
        return EnumSet.of(StatusEnum.ENFORCING_SUCCESS);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        //手动触发前置状态
        return EnumSet.of(StatusEnum.INIT_FAILURE, StatusEnum.SUBMIT_FAILURE, StatusEnum.RUN_FAILURE,
                StatusEnum.STOP_SUCCESS, StatusEnum.STOP_FAILURE, StatusEnum.KILLED, StatusEnum.UNKNOWN);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        //处理worker结果消息
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        TaskResult acceptState = msg.getTaskResult();
        if (acceptState == null) {
            saveEnforceSuccess(taskIns, context);
            return;
        }
        if (null != acceptState) {
            if (StatusEnum.ENFORCE_SUCCESS == acceptState.getTaskState()) {
                saveEnforceSuccess(taskIns, context);
                return;
            }
        }
        log.error("不可能发生!!!程序异常!!!");
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        saveEnforceSuccess(taskIns, context);
    }

    private void saveEnforceSuccess(TaskInstance taskIns, ActorSysContext context) {
        taskIns.setState(StatusEnum.ENFORCE_SUCCESS);
        super.saveTaskInstance(taskIns);
        FlowMsg flowMsg = FlowMsg.builder()
                .flowInstanceId(taskIns.getFlowInstanceId())
                //.flowId(taskIns.getFlowId())
                .taskState(Pair.of(taskIns.getInstanceId(), StatusEnum.ENFORCE_SUCCESS))
                .actionType(ActionType.RUN)
                .isManualAction(false)
                .build();
        super.notifyFlowActor(flowMsg, context);
        super.notifyNextTasks(taskIns, context);
    }
}
