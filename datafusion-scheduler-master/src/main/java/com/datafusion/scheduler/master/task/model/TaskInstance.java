package com.datafusion.scheduler.master.task.model;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.param.builtin.BuiltinParamEnum;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Variable;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * task实例.
 *
 * @author 李正凯
 * @version 3.0.0, 2022/4/22
 * @since 2022/4/22
 */
@Data
public class TaskInstance {

    /**
     * 默认重试次数.
     */
    private static final Integer DEFAULT_MAX_RETRY_TIMES = 3;

    /**
     * 默认重试间隔.
     */
    private static final Long DEFAULT_RETRY_INTERVAL = 10000L;

    /**
     * instanceId.
     */
    private String instanceId;

    /**
     * 流程id.
     */
    //private String flowId;

    /**
     * flow instance id.
     */
    private String flowInstanceId;

    /**
     * 任务类型.
     */
    private String taskType;

    /**
     * 任务id.
     */
    private String taskId;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 任务说明.
     */
    private String taskDesc;

    /**
     * 当前状态.
     */
    private StatusEnum state;

    /*
     * 每个状态的处理时间,上一状态时间和下一状态时间.用于统计分析(是否需要?)
     */
    // private Long doStateTime;

    /**
     * 开始时间.
     */
    private Long startTime;

    /**
     * 结束时间.
     */
    private Long endTime;

    /**
     * 执行时间.
     */
    private Long costTime;

    /**
     * 重试次数.
     */
    private Integer retryTimes = 0;

    /**
     * 最大重试次数.
     */
    private Integer maxRetryTimes = DEFAULT_MAX_RETRY_TIMES;

    /**
     * 重试间隔.
     */
    private Long retryInterval = DEFAULT_RETRY_INTERVAL;

    /**
     * 超时时间.
     */
    private Long timeout;

    /**
     * 上游任务实例 (即依赖的task instanceId).
     */
    private Set<String> lastInstanceIds;

    /**
     * 下游任务实例 (可同时执行的实例).
     */
    private Set<String> nextInstanceIds;

    /**
     * 全局依赖事件.
     */
    private Set<String> depEventIds;

    /**
     * task定义的业务事件id.
     */
    private String eventId;

    /**
     * 运行期任务变量参数.
     */
    private ParamData taskParam;

    /**
     * 渲染后的任务执行数据.
     */
    private JsonNode taskData;

    /**
     * 业务结果.
     */
    private TaskResult taskResult;

    /**
     * task组件.
     */
    private PluginData pluginData;

    /**
     * 是否启用.
     */
    private Boolean isAble = true;

    /**
     * 获取事件时间,目前事件时间与业务开始时间一致.
     * TODO 若没有后期是否就是 scheduleTime.
     *
     * @return 事件时间
     */
    public Long eventTime() {
        Long eventTime = null;
        if (this.getTaskParam() != null) {
            Map<String, Variable> vars = taskParam.getVars();
            if (vars != null) {
                Variable v = vars.get(BuiltinParamEnum.BIZ_DATE.getParamName());
                if (v != null) {
                    eventTime = Long.parseLong(v.getValue());
                    return eventTime;
                }
            }
        }
        return eventTime;
    }

    /**
     * 获取事件时间周期.
     * TODO 若没有后期是否可以自动计算.
     *
     * @return 时间段
     */
    public String eventSegment() {
        String eventSegment = null;
        if (this.getTaskParam() != null) {
            Map<String, Variable> vars = taskParam.getVars();
            if (vars != null) {
                Variable v = vars.get(BuiltinParamEnum.BIZ_DATE_ALIGN.getParamName());
                if (v != null) {
                    eventSegment = String.valueOf(v.getValue());
                    return eventSegment;
                }
            }
        }
        return eventSegment;
    }

    /**
     * 是否设置了超时时间.
     *
     * @return true：设置了 false：未设置
     */
    public boolean isSetTimeout() {
        return timeout != null && timeout > 0;
    }

    /**
     * 与当前时间戳比是否超时.
     *
     * @return 结果
     */
    public boolean isTimeout() {
        return timeout > 0 && timeout < System.currentTimeMillis();
    }

    /**
     * 判断是否有下一个任务.
     *
     * @return true:有 false:无
     */
    public boolean hasNextTask() {
        return CollectionUtil.isNotEmpty(nextInstanceIds);
    }

    /**
     * 判断是否能重试.
     *
     * @return 结果
     */
    public boolean canRetry() {
        // todo 待补充
        return (this.getRetryTimes() != null && this.getRetryTimes() < this.getMaxRetryTimes());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskInstance that = (TaskInstance) o;
        return instanceId.equals(that.instanceId) && flowInstanceId.equals(that.flowInstanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, flowInstanceId);
    }

    @Override
    public String toString() {
        return "TaskInstance{" + "taskId='" + taskId + '\'' + ", instanceId=" + instanceId + ", state=" + state + ", retryTimes=" + retryTimes + '}';
    }
}
