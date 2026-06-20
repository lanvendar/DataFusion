package com.datafusion.scheduler.master.flow.model;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.flow.enums.FlowTypeEnum;
import com.datafusion.scheduler.master.variable.SchedulerBuiltinVariableEnum;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.Variable;
import lombok.Data;

import java.util.Objects;
import java.util.Set;

/**
 * flow 实例.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/6/15
 * @since 2022/6/15
 */
@Data
public class FlowInstance implements Comparable<FlowInstance> {

    //region schedule调度属性

    /**
     * flow instance id.
     */
    private String instanceId;

    /**
     * 流程类型:1流任务;2批任务.
     */
    private FlowTypeEnum flowType;

    /**
     * 调度时间.
     */
    private Long scheduleTime;

    /**
     * 被调度对象的版本.
     */
    private String version;

    /**
     * 开始时间.
     */
    private Long startTime;

    /**
     * 结束时间.
     */
    private Long endTime;

    /**
     * 状态.
     */
    private StatusEnum state;
    //endregion
    //region flowInfo流程属性

    /**
     * flow id.
     */
    private String flowId;

    /**
     * flow 名称.
     */
    private String flowName;

    /**
     * 流程变量参数.
     */
    private ParamData flowParam;

    /**
     * 全局依赖事件.
     */
    private Set<String> depEventIds;

    /**
     * flow定义的业务事件id.
     */
    private String eventId;

    /**
     * flow全局组件.
     */
    private PluginData pluginData;
    //endregion

    /**
     * 获取事件时间.
     *
     * @return 事件时间
     */
    public Long eventTime() {
        return longValue(SchedulerBuiltinVariableEnum.EVENT_TIME);
    }

    /**
     * 获取事件时间周期.
     *
     * @return 时间段
     */
    public String eventSegment() {
        return stringValue(SchedulerBuiltinVariableEnum.EVENT_ALIGN);
    }

    /**
     * 获取长整型变量值.
     *
     * @param variable 内置变量
     * @return 长整型变量值
     */
    private Long longValue(SchedulerBuiltinVariableEnum variable) {
        String value = stringValue(variable);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取字符串变量值.
     *
     * @param variable 内置变量
     * @return 字符串变量值
     */
    private String stringValue(SchedulerBuiltinVariableEnum variable) {
        if (flowParam == null || flowParam.getVars() == null) {
            return null;
        }
        Variable value = flowParam.getVars().get(variable.getParamName());
        return value == null ? null : value.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FlowInstance that = (FlowInstance) o;
        return instanceId.equals(that.instanceId) && flowId.equals(that.flowId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId, instanceId);
    }

    @Override
    public int compareTo(FlowInstance o) {
        return (int) (this.getScheduleTime() - o.getScheduleTime());
    }

    @Override
    public String toString() {
        return "FlowInstance{" + "flowId='" + flowId + '\'' + ", instanceId=" + instanceId + ", scheduleTime="
                + scheduleTime + ", state=" + state + '}';
    }
}
