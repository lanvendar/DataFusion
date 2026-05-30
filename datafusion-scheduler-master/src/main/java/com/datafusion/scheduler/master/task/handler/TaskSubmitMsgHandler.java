package com.datafusion.scheduler.master.task.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Pair;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务提交消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskSubmitMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskSubmitMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.SUBMIT;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作可以从 WAIT_DEPENDENT, RETRYING, RESTARTING 提交
        return EnumSet.of(StatusEnum.WAIT_DEPENDENT, StatusEnum.RESTARTING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从 INIT_SUCCESS, WAIT_DEPENDENT 提交
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.WAIT_DEPENDENT);
        //return EnumSet.of(StateEnum.INIT_SUCCESS, StateEnum.WAIT_DEPENDENT, StateEnum.WAIT_RESOURCES);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = super.getTaskInstance(msg.getTaskInstanceId());
        taskIns.setState(StatusEnum.SUBMITTING);
        super.saveTaskInstance(taskIns);
        //通知流程,任务的状态消息
        Pair<String, StatusEnum> stateSubmitting = Pair.of(msg.getTaskInstanceId(), StatusEnum.SUBMITTING);
        FlowMsg msgSubmitting = FlowMsg.builder()
                .flowInstanceId(msg.getFlowInstanceId())
                .actionType(ActionType.RUN)
                .isManualAction(false)
                .taskState(stateSubmitting)
                .build();
        super.notifyFlowActor(msgSubmitting, context);

        try {
            TaskResult taskResult = super.masterTaskOperator.runTask(renderTaskParam(taskIns));
            //处理 worker 端返回的同步和异步任务结果
            StatusEnum taskState = StatusEnum.SUBMIT_SUCCESS;
            if (null != taskResult) {
                if (taskResult.isSync()) {
                    taskState = taskResult.getTaskState();
                }
                taskIns.setState(taskState);
                taskIns.setTaskResult(taskResult);
            }
            super.saveTaskInstance(taskIns);
            //通知流程,任务的状态消息
            Pair<String, StatusEnum> stateSuccess = Pair.of(msg.getTaskInstanceId(), taskState);
            FlowMsg msgSuccess = FlowMsg.builder()
                    .flowInstanceId(msg.getFlowInstanceId())
                    .actionType(ActionType.RUN)
                    .isManualAction(false)
                    .taskState(stateSuccess)
                    .build();
            super.notifyFlowActor(msgSuccess, context);
        } catch (Exception e) {
            log.warn("[{}] - 任务提交失败, 准备开始重试.", taskIns.getInstanceId(), e);
            taskIns.setState(StatusEnum.SUBMIT_FAILURE);
            super.saveTaskInstance(taskIns);
            TaskMsg retryMsg = TaskMsg.builder()
                    .taskInstanceId(taskIns.getInstanceId())
                    .actionType(ActionType.RESTART)
                    .build();
            context.notify(retryMsg);
        }
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        //忽略依赖提交
        msg.setManualAction(false);
        context.notify(msg);
    }

    /**
     * 合并渲染全局参数和局部参数. 即优先取task自己的变量付给自己的参数,如果没有则从flow的变量中取.
     *
     * @param taskIns task instance
     * @return 返回一个深拷贝后新的 task instance
     */
    protected TaskInstance renderTaskParam(TaskInstance taskIns) {
        log.debug("根据task id获取完整task param，taskInstanceId={}", taskIns.getInstanceId());
        String taskParam = JacksonUtils.tryObj2Str(taskIns.getTaskData().getParams());

        Map<String, String> fullInputVarMap = new HashMap<>(2);
        // TODO 待实现
        TaskInstance newTaskIns = new TaskInstance();
        BeanUtil.copyProperties(taskIns, newTaskIns);

        newTaskIns.setTaskData(new ParamData());
        newTaskIns.getTaskData().setParams(JacksonUtils.tryStr2JsonNode(taskParam));
        return newTaskIns;
    }
}
