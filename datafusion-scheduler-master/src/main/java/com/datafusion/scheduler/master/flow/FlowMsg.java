package com.datafusion.scheduler.master.flow;

import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorMsg;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/**
 * 流程消息.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Getter
@Builder
public class FlowMsg implements ActorMsg {

    /**
     * 流程实例ID/actorId.
     */
    @NotNull(message = "流程实例ID不能为空")
    private String flowInstanceId;

    /**
     * 流程ID.
     */
    private String flowId;

    /**
     * 调度版本.
     */
    private String version;

    /**
     * 调度时间.
     */
    private long scheduleTime;

    /**
     * 任务状态集合.
     * Key: taskInstanceId, Value: StateEnum
     */
    private Pair<String, StatusEnum> taskState;

    /**
     * 根据当前任务状态计算后的流程统计状态 .
     */
    @Setter
    private StatusEnum flowTargetState;

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

    @Override
    public String getMsgType() {
        return actionType != null ? actionType.flowType() : null;
    }
}
