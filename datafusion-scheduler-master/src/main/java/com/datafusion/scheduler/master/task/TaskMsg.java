package com.datafusion.scheduler.master.task;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.TaskResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/**
 * 任务消息.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Getter
@Builder
public class TaskMsg implements ActorMsg {
    /**
     * 任务ID.
     */
    private String taskId;

    /**
     * 任务实例ID.
     */
    @NotNull(message = "任务实例ID不能为空")
    private String taskInstanceId;

    /**
     * 流程实例ID.
     */
    private String flowInstanceId;

    /**
     * 流程变量参数.
     */
    private ParamData flowParamData;

    /**
     * 调度版本.
     */
    private String version;

    /**
     * 调度时间.
     */
    private long scheduleTime;

    /**
     * 调度动作类型.
     */
    @NotNull(message = "消息动作类型不能为空")
    private ActionType actionType;

    /**
     * 是否手工的动作.
     * true: 外部手工动作消息
     * false: 内部消息
     */
    @Setter
    @NotNull(message = "是否手工的动作不能为空")
    private boolean isManualAction;

    /**
     * 任务结果.
     */
    private TaskResult taskResult;

    @Override
    public String getMsgType() {
        return actionType != null ? actionType.taskType() : null;
    }
}
