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
 * 任务停止消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskStopMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskStopMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.STOP;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.INIT_FAILURE, StatusEnum.WAIT_DEPENDENT,
                StatusEnum.SUBMIT_SUCCESS, StatusEnum.SUBMIT_FAILURE, StatusEnum.RUNNING, StatusEnum.STOPPING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // SUBMITTING 无法确认是否已与 worker 完成交互，不允许手工停止，需等待进入其他可停止状态后再处理
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.INIT_FAILURE, StatusEnum.WAIT_DEPENDENT,
                StatusEnum.SUBMIT_SUCCESS, StatusEnum.SUBMIT_FAILURE, StatusEnum.RUNNING);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        if (msg.getTaskResult() == null) {
            handleRecoveryStop(taskIns, context);
            return;
        }
        handleWorkerStopResult(taskIns, msg.getTaskResult(), context);
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = getTaskInstance(msg.getTaskInstanceId());
        handleManualStop(taskIns, context);
    }

    /**
     * 根据恢复时保存的任务状态继续停止流程，已处于 {@link StatusEnum#STOPPING} 的任务会幂等重试 Worker 停止请求.
     *
     * @param taskIns 任务实例
     * @param context Actor 上下文
     */
    private void handleRecoveryStop(TaskInstance taskIns, ActorSysContext context) {
        switch (taskIns.getState()) {
            case INIT_SUCCESS:
            case INIT_FAILURE:
            case WAIT_DEPENDENT:
                stopDirectly(taskIns, context);
                break;
            case SUBMIT_SUCCESS:
            case SUBMIT_FAILURE:
            case RUNNING:
            case STOPPING:
                requestWorkerStop(taskIns, context);
                break;
            default:
                log.warn("[{}] - 当前状态不支持恢复停止: {}", taskIns.getInstanceId(), taskIns.getState());
                break;
        }
    }

    /**
     * 处理手工停止，未提交的任务直接停止，已与 Worker 交互的任务请求 Worker 停止.
     *
     * @param taskIns 任务实例
     * @param context Actor 上下文
     */
    private void handleManualStop(TaskInstance taskIns, ActorSysContext context) {
        switch (taskIns.getState()) {
            case INIT_SUCCESS:
            case INIT_FAILURE:
            case WAIT_DEPENDENT:
                stopDirectly(taskIns, context);
                break;
            case SUBMIT_SUCCESS:
            case SUBMIT_FAILURE:
            case RUNNING:
                requestWorkerStop(taskIns, context);
                break;
            default:
                log.warn("[{}] - 当前状态不支持手工停止: {}", taskIns.getInstanceId(), taskIns.getState());
                break;
        }
    }

    /**
     * 将无需与 Worker 交互的任务直接完成为 {@link StatusEnum#STOP_SUCCESS}.
     *
     * @param taskIns 任务实例
     * @param context Actor 上下文
     */
    private void stopDirectly(TaskInstance taskIns, ActorSysContext context) {
        updateStateAndNotifyFlow(taskIns, StatusEnum.STOP_SUCCESS, null, context);
    }

    /**
     * 将任务推进到 {@link StatusEnum#STOPPING} 并请求 Worker 停止，同步返回的结果会立即进入状态分流.
     *
     * @param taskIns 任务实例
     * @param context Actor 上下文
     */
    private void requestWorkerStop(TaskInstance taskIns, ActorSysContext context) {
        try {
            if (taskIns.getState() != StatusEnum.STOPPING) {
                updateStateAndNotifyFlow(taskIns, StatusEnum.STOPPING, null, context);
            }
            TaskResult taskResult = super.masterTaskOperator.stopTask(taskIns);
            if (taskResult != null && taskResult.getTaskState() != null) {
                handleWorkerStopResult(taskIns, taskResult, context);
            }
        } catch (Exception e) {
            log.warn("[{}] - 停止任务失败", taskIns.getInstanceId(), e);
            updateStateAndNotifyFlow(taskIns, StatusEnum.STOP_FAILURE, null, context);
        }
    }

    /**
     * 处理 Worker 停止结果：停止终态直接收敛，运行终态转交 RUN handler，STOPPING 等待异步上报.
     *
     * @param taskIns   任务实例
     * @param taskResult Worker 返回的任务结果
     * @param context   Actor 上下文
     */
    private void handleWorkerStopResult(TaskInstance taskIns, TaskResult taskResult, ActorSysContext context) {
        StatusEnum acceptState = taskResult.getTaskState();
        if (acceptState == null) {
            log.warn("[{}] - 收到无状态的停止结果", taskIns.getInstanceId());
            return;
        }
        switch (acceptState) {
            case STOPPING:
                log.debug("[{}] - 停止请求处理中，等待 worker 异步上报", taskIns.getInstanceId());
                break;
            case STOP_SUCCESS:
            case STOP_FAILURE:
                updateStateAndNotifyFlow(taskIns, acceptState, taskResult, context);
                break;
            case RUN_SUCCESS:
            case RUN_FAILURE:
            case UNKNOWN:
                forwardToRunHandler(taskIns, taskResult, context);
                break;
            default:
                log.warn("[{}] - 收到停止协议外状态: {}", taskIns.getInstanceId(), acceptState);
                break;
        }
    }

    /**
     * 将停止竞态产生的运行终态转交 RUN handler 处理.
     *
     * @param taskIns   任务实例
     * @param taskResult Worker 返回的运行终态结果
     * @param context   Actor 上下文
     */
    private void forwardToRunHandler(TaskInstance taskIns, TaskResult taskResult, ActorSysContext context) {
        log.debug("[{}] - 转交 runHandler 处理: {}", taskIns.getInstanceId(), taskResult.getTaskState());
        TaskMsg runMsg = TaskMsg.builder()
                .flowInstanceId(taskIns.getFlowInstanceId())
                .taskInstanceId(taskIns.getInstanceId())
                .actionType(ActionType.RUN)
                .isManualAction(false)
                .taskResult(taskResult)
                .build();
        context.notify(runMsg);
    }

    /**
     * 更新任务状态和可选任务结果，持久化后通知流程 Actor.
     *
     * @param taskIns   任务实例
     * @param state     目标状态
     * @param taskResult Worker 返回的任务结果，可为空
     * @param context   Actor 上下文
     */
    private void updateStateAndNotifyFlow(TaskInstance taskIns, StatusEnum state, TaskResult taskResult, ActorSysContext context) {
        taskIns.setState(state);
        if (taskResult != null) {
            taskIns.setTaskResult(taskResult);
        }
        super.saveTaskInstance(taskIns);
        FlowMsg flowMsg = FlowMsg.builder()
                .flowInstanceId(taskIns.getFlowInstanceId())
                .taskState(Pair.of(taskIns.getInstanceId(), state))
                .actionType(ActionType.RUN)
                .isManualAction(false)
                .build();
        super.notifyFlowActor(flowMsg, context);
    }
}
