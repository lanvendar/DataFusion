package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.master.variable.PlaceholderContext;
import com.datafusion.scheduler.master.variable.SchedulerBuiltinVariableEnum;
import com.datafusion.scheduler.master.variable.SchedulerVariableResolver;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务初始化消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskInitMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 调度变量解析器.
     */
    private final SchedulerVariableResolver schedulerVariableResolver = new SchedulerVariableResolver();

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param masterTaskOperator  任务执行器
     */
    public TaskInitMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator) {
        super(taskStorage, eventOperator, masterTaskOperator);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.INIT;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        // 自动动作只能从 INITIALIZING 状态开始
        return EnumSet.of(StatusEnum.INITIALIZING);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        // 手工动作可以从 INIT_FAILURE 重新初始化
        return EnumSet.of(StatusEnum.INIT_FAILURE);
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = super.getTaskInstance(msg.getTaskInstanceId());
        if (taskIns != null) {
            // 删除任务实例
            super.removeInstance(taskIns.getInstanceId());
        }
        TaskInfo taskInfo = super.getTaskInfo(msg.getTaskId());
        TaskInstance newTaskIns = createTaskInstance(msg, taskInfo);
        super.saveTaskInstance(newTaskIns);
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        handleAction(msg, context);
    }

    /**
     * 组装任务实例.
     *
     * @param msg      任务消息
     * @param taskInfo 任务信息
     */
    private TaskInstance createTaskInstance(TaskMsg msg, TaskInfo taskInfo) {
        TaskInstance taskIns = new TaskInstance();
        taskIns.setInstanceId(msg.getTaskInstanceId());
        //taskIns.setFlowId(flowId);
        taskIns.setFlowInstanceId(msg.getFlowInstanceId());
        taskIns.setTaskType(taskInfo.getTaskType());
        taskIns.setTaskId(taskInfo.getTaskId());
        taskIns.setTaskName(taskInfo.getTaskName());
        taskIns.setTaskDesc(taskInfo.getTaskDesc());
        taskIns.setState(StatusEnum.INITIALIZING);
        taskIns.setRetryTimes(0);
        taskIns.setTimeout(null);
        // 设置任务的事件依赖
        taskIns.setDepEventIds(taskInfo.getDepEventIds());
        taskIns.setEventId(taskInfo.getEventId());
        // taskIns.setEventTime(taskInfo.getEventTime());
        // taskIns.setStartTime(null);
        // taskIns.setEndTime(null);
        // 合并流程变量至任务变量, 包含业务时间, 变量冲突时优先取任务变量.
        taskIns.setTaskParam(mergeTaskParam(taskInfo.getTaskParam(), msg.getFlowParamData(), msg.getScheduleTime()));
        taskIns.setTaskData(taskInfo.getDefinition());
        taskIns.setPluginData(taskInfo.getPluginData());
        taskIns.setTaskResult(null);
        return taskIns;
    }

    /**
     * 合并任务变量和流程变量.
     *
     * @param taskParam 任务变量
     * @param flowParam 流程变量
     * @param scheduleTime 调度时间
     * @return 合并后的任务变量
     */
    private ParamData mergeTaskParam(ParamData taskParam, ParamData flowParam, long scheduleTime) {
        ParamData result = new ParamData();
        Map<String, Variable> mergedVars = new HashMap<>();
        mergedVars.putAll(copyExplicitVars(flowParam));
        mergedVars.putAll(copyVars(taskParam));
        result.setVars(mergedVars);
        PlaceholderContext context = PlaceholderContext.builder()
                .scheduleTime(scheduleTime)
                .variables(result.getVars())
                .build();
        schedulerVariableResolver.resolveBuiltinVariables(context);
        return result;
    }

    /**
     * 拷贝显式配置的变量Map.
     *
     * @param paramData 参数对象
     * @return 变量Map
     */
    private Map<String, Variable> copyExplicitVars(ParamData paramData) {
        Map<String, Variable> copiedVars = new HashMap<>();
        if (paramData == null || paramData.getVars() == null) {
            return copiedVars;
        }
        paramData.getVars().forEach((key, value) -> {
            if (!isGeneratedBuiltinVariable(key, value)) {
                copiedVars.put(key, copyVariable(value));
            }
        });
        return copiedVars;
    }

    /**
     * 判断是否为解析器生成的内置变量.
     *
     * @param name     变量名
     * @param variable 变量对象
     * @return 是否为解析器生成的内置变量
     */
    private boolean isGeneratedBuiltinVariable(String name, Variable variable) {
        if (variable == null || variable.getType() != null) {
            return false;
        }
        return SchedulerBuiltinVariableEnum.getByParamName(name) != null
                || SchedulerBuiltinVariableEnum.getByParamName(variable.getName()) != null;
    }

    /**
     * 拷贝变量Map.
     *
     * @param paramData 参数对象
     * @return 变量Map
     */
    private Map<String, Variable> copyVars(ParamData paramData) {
        Map<String, Variable> copiedVars = new HashMap<>();
        if (paramData == null || paramData.getVars() == null) {
            return copiedVars;
        }
        paramData.getVars().forEach((key, value) -> copiedVars.put(key, copyVariable(value)));
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
