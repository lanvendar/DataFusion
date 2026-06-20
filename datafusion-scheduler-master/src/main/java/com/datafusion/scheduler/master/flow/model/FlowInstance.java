package com.datafusion.scheduler.master.flow.model;

import com.datafusion.common.variable.builtin.BuiltinVariableEnum;
import com.datafusion.common.variable.builtin.BuiltinTimeParams;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.flow.enums.FlowTypeEnum;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.Variable;
import lombok.Data;

import java.util.Map;
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

    /**
     * 内置时间变量求值工具.
     */
    private static final BuiltinTimeParams BUILTIN_TIME_PARAMS = new BuiltinTimeParams();

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
        Long eventTime = null;
        if (this.getFlowParam() != null) {
            Map<String, Variable> vars = flowParam.getVars();
            if (vars != null) {
                Variable v = vars.get(BuiltinVariableEnum.EVENT_TIME.getParamName());
                if (v != null) {
                    eventTime = BUILTIN_TIME_PARAMS.parseLong(v.getValue());
                    if (eventTime != null) {
                        return eventTime;
                    }
                }
                return alignedEventTime();
            }
        }
        return eventTime;
    }

    /**
     * 获取事件时间周期.
     *
     * @return 时间段
     */
    public String eventSegment() {
        String eventSegment = null;
        if (this.getFlowParam() != null) {
            Map<String, Variable> vars = flowParam.getVars();
            if (vars != null) {
                Variable v = vars.get(BuiltinVariableEnum.EVENT_ALIGN.getParamName());
                if (v != null) {
                    eventSegment = String.valueOf(v.getValue());
                    return eventSegment;
                }
            }
        }
        return eventSegment;
    }

    /**
     * 根据调度时间和事件对齐格式计算事件时间.
     *
     * @return 事件时间
     */
    private Long alignedEventTime() {
        if (scheduleTime == null) {
            return null;
        }
        return BUILTIN_TIME_PARAMS.eventTime(toCommonVariables(), scheduleTime);
    }

    /**
     * 转换为通用变量映射.
     *
     * @return 通用变量映射
     */
    private Map<String, Variable> toCommonVariables() {
        return flowParam == null ? null : flowParam.getVars();
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
