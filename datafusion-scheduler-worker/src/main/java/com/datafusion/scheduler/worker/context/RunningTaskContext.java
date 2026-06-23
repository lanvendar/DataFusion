package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Variable;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

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
     * 工作节点 ID.
     */
    private String workerId;

    /**
     * 插件类型.
     */
    private String pluginType;

    /**
     * agent 终端运行模式.
     */
    private String runMode;

    /**
     * 终端任务 ID.
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
     * 是否已经提交过.
     */
    private boolean submitted;

    /**
     * 渲染后的任务执行数据.
     */
    private JsonNode taskData;

    /**
     * 插件参数.
     */
    private JsonNode pluginParam;

    /**
     * 输出变量列表.
     */
    private Map<String, Variable> outputVars;

    /**
     * 任务运行目录路径.
     */
    private String workDirPath;

    /**
     * 执行结果说明.
     */
    private JsonNode result;

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
        context.setWorkerId(request.getWorkerId());
        context.setPluginType(request.getPluginType());
        context.setAppId(request.getAppId());
        context.setTaskState(request.getTaskState());
        context.setSubmitMode(request.getSubmitMode());
        context.setTaskData(request.getTaskData());
        context.setPluginParam(request.getPluginParam());
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
        this.submitted = true;
        if (result != null) {
            if (result.getTaskState() != null) {
                this.taskState = result.getTaskState();
            }
            if (result.getWorkerId() != null) {
                this.workerId = result.getWorkerId();
            }
            if (result.getAppId() != null) {
                this.appId = result.getAppId();
            }
            if (result.getOutputVars() != null) {
                this.outputVars = result.getOutputVars();
            }
            if (result.getWorkDirPath() != null) {
                this.workDirPath = result.getWorkDirPath();
            }
            if (result.getResult() != null) {
                this.result = result.getResult();
            }
            if (result.getSubmitMode() != null) {
                this.submitMode = result.getSubmitMode();
            }
        }
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 更新任务请求快照.
     *
     * @param request 任务请求
     */
    public void updateRequest(TaskRequest request) {
        if (request == null) {
            return;
        }
        if (request.getFlowInstanceId() != null) {
            this.flowInstanceId = request.getFlowInstanceId();
        }
        if (request.getTaskName() != null) {
            this.taskName = request.getTaskName();
        }
        if (request.getWorkerId() != null) {
            this.workerId = request.getWorkerId();
        }
        if (request.getPluginType() != null) {
            this.pluginType = request.getPluginType();
        }
        if (request.getAppId() != null) {
            this.appId = request.getAppId();
        }
        if (request.getTaskState() != null) {
            this.taskState = request.getTaskState();
        }
        if (request.getSubmitMode() != null) {
            this.submitMode = request.getSubmitMode();
        }
        if (request.getTaskData() != null) {
            this.taskData = request.getTaskData();
        }
        if (request.getPluginParam() != null) {
            this.pluginParam = request.getPluginParam();
        }
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 根据当前上下文构建任务结果.
     *
     * @return 任务结果
     */
    public TaskResult toTaskResult() {
        return TaskResult.builder()
                .taskInstanceId(taskInstanceId)
                .flowInstanceId(flowInstanceId)
                .taskName(taskName)
                .taskState(taskState)
                .outputVars(outputVars)
                .workerId(workerId)
                .appId(appId)
                .workDirPath(workDirPath)
                .submitMode(submitMode)
                .result(result)
                .build();
    }

    /**
     * 使用上下文补齐任务请求.
     *
     * @param request 任务请求
     * @return 补齐后的任务请求
     */
    public TaskRequest fillRequest(TaskRequest request) {
        if (request.getFlowInstanceId() == null) {
            request.setFlowInstanceId(flowInstanceId);
        }
        if (request.getTaskName() == null) {
            request.setTaskName(taskName);
        }
        if (request.getWorkerId() == null) {
            request.setWorkerId(workerId);
        }
        if (request.getTaskState() == null) {
            request.setTaskState(taskState);
        }
        if (request.getAppId() == null) {
            request.setAppId(appId);
        }
        if (request.getPluginType() == null) {
            request.setPluginType(pluginType);
        }
        if (request.getTaskData() == null) {
            request.setTaskData(taskData);
        }
        if (request.getPluginParam() == null) {
            request.setPluginParam(pluginParam);
        }
        if (request.getSubmitMode() == null) {
            request.setSubmitMode(submitMode);
        }
        return request;
    }
}
