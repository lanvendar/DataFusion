package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import lombok.Data;

/**
 * Worker 本地运行中任务上下文.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Data
public class RunningTaskContext {

    /**
     * 任务实例ID.
     */
    private String taskInstanceId;

    /**
     * 流程实例ID.
     */
    private String flowInstanceId;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 插件类型.
     */
    private String pluginType;

    /**
     * 第三方插件运行唯一主键 appId.
     */
    private String appId;

    /**
     * 最近任务状态.
     */
    private StatusEnum taskState;

    /**
     * 提交模式.
     */
    private SubmitModeEnum submitMode;

    /**
     * 原始请求.
     */
    private TaskRequest request;

    /**
     * 最近一次任务结果.
     */
    private TaskResult lastResult;

    /**
     * 创建时间.
     */
    private Long createTime;

    /**
     * 更新时间.
     */
    private Long updateTime;

    /**
     * 根据任务请求创建上下文.
     *
     * @param request 任务请求
     * @return 运行中任务上下文
     */
    public static RunningTaskContext fromRequest(TaskRequest request) {
        long now = System.currentTimeMillis();
        RunningTaskContext context = new RunningTaskContext();
        context.setTaskInstanceId(request.getTaskInstanceId());
        context.setFlowInstanceId(request.getFlowInstanceId());
        context.setTaskName(request.getTaskName());
        context.setPluginType(request.getPluginType());
        context.setAppId(request.getAppId());
        context.setTaskState(request.getTaskState());
        context.setSubmitMode(request.getSubmitMode());
        context.setRequest(request);
        context.setCreateTime(now);
        context.setUpdateTime(now);
        return context;
    }

    /**
     * 更新最近结果.
     *
     * @param result 任务结果
     */
    public void updateResult(TaskResult result) {
        this.lastResult = result;
        if (result != null) {
            this.taskState = result.getTaskState();
            this.appId = result.getAppId();
        }
        this.updateTime = System.currentTimeMillis();
    }
}
