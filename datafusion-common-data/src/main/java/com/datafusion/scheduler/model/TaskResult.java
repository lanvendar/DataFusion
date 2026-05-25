package com.datafusion.scheduler.model;

import com.datafusion.scheduler.enums.StatusEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 任务结果参数.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/7/26
 * @since 2022/7/26
 */
@Data
@Builder
public class TaskResult {
    //region 请求和返回公共字段

    /**
     * task instance id.
     */
    private String taskInstanceId;

    /**
     * flow instance id.
     */
    private String flowInstanceId;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 任务执行状态.
     */
    private StatusEnum taskState;
    //endregion
    //region 返回字段

    /**
     * 输出变量列表.
     */
    private Map<String, Variable> outputVars;

    /**
     * 获取worker id.
     */
    private String workerId;

    /**
     * 返回执行的application id.
     */
    private String appId;

    /**
     * worker 端是否同步执行任务的标志,默认为同步执行.
     */
    private boolean isSync = true;

    /**
     * 执行节点返回结果.
     */
    private String result;
    //endregion
}
