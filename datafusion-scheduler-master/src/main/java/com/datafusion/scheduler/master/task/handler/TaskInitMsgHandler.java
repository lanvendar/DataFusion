package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.task.TaskExecutor;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param taskExecutor  任务执行器
     */
    public TaskInitMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, TaskExecutor taskExecutor) {
        super(taskStorage, eventOperator, taskExecutor);
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
        ParamData taskParamData = taskInfo.getTaskParam();
        // 合并流程参数至任务参数,包含业务时间.
        if (null != msg.getFlowParamData()) {
            Map<String, Variable> flowVars = Optional.ofNullable(msg.getFlowParamData().getVars()).orElseGet(HashMap::new);
            Map<String, Variable> taskVars = Optional.ofNullable(taskParamData.getVars()).orElseGet(HashMap::new);

            // 合并参数,优先取task中的参数
            Map<String, Variable> mergedMap = Stream.concat(taskVars.entrySet().stream(), flowVars.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
            taskParamData.setVars(mergedMap);
        }
        taskIns.setTaskData(taskParamData);
        taskIns.setTaskResult(null);
        return taskIns;
    }
}
