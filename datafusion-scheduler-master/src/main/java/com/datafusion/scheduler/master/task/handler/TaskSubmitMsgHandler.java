package com.datafusion.scheduler.master.task.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Pair;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.FlowMsg;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.master.variable.PlaceholderContext;
import com.datafusion.scheduler.master.variable.SchedulerVariableFacade;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Variable;
import com.fasterxml.jackson.databind.JsonNode;
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
            TaskInstance submitTaskIns = renderTaskParam(taskIns);
            TaskResult taskResult = super.masterTaskOperator.submitTask(submitTaskIns);
            //处理 worker 端返回的同步和异步任务结果
            StatusEnum taskState = StatusEnum.SUBMIT_SUCCESS;
            taskIns.setTaskData(submitTaskIns.getTaskData());
            if (null != taskResult) {
                if (taskResult.getSubmitMode() == SubmitModeEnum.SYNC) {
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
        TaskInstance newTaskIns = new TaskInstance();
        BeanUtil.copyProperties(taskIns, newTaskIns);
        newTaskIns.setTaskData(renderDefinition(taskIns));
        return newTaskIns;
    }

    /**
     * 渲染任务定义.
     *
     * @param taskIns task instance
     * @return 渲染后的任务定义
     */
    private JsonNode renderDefinition(TaskInstance taskIns) {
        JsonNode definition = taskIns.getTaskData();
        if (definition == null || definition.isNull()) {
            return definition;
        }

        String definitionText = JacksonUtils.tryObj2Str(definition);
        PlaceholderContext context = PlaceholderContext.builder()
                .variables(copyVars(taskIns))
                .build();
        String renderedDefinition = SchedulerVariableFacade.getInstance().replacePlaceholders(definitionText, context);
        return JacksonUtils.tryStr2JsonNode(renderedDefinition);
    }

    /**
     * 拷贝任务变量.
     *
     * @param taskIns task instance
     * @return 变量Map
     */
    private Map<String, Variable> copyVars(TaskInstance taskIns) {
        Map<String, Variable> copiedVars = new HashMap<>();
        if (taskIns.getTaskParam() == null || taskIns.getTaskParam().getVars() == null) {
            return copiedVars;
        }
        taskIns.getTaskParam().getVars().forEach((key, value) -> copiedVars.put(key, copyVariable(value)));
        return copiedVars;
    }

    /**
     * 拷贝变量对象.
     *
     * @param source 原变量
     * @return 新变量
     */
    private Variable copyVariable(Variable source) {
        if (source == null) {
            return null;
        }
        Variable target = new Variable();
        target.setName(source.getName());
        target.setType(source.getType());
        target.setValue(source.getValue());
        return target;
    }
}
