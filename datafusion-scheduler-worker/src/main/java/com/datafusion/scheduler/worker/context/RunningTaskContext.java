package com.datafusion.scheduler.worker.context;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Variable;
import com.datafusion.scheduler.model.WorkerResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        WorkerResult workerResult = request.getWorkerResult();
        context.setWorkerId(workerResult == null ? null : workerResult.getWorkerId());
        context.setPluginType(request.getPluginType());
        context.setAppId(workerResult == null ? null : workerResult.getAppId());
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
            WorkerResult workerResult = result.getWorkerResult();
            if (result.getTaskState() != null) {
                this.taskState = result.getTaskState();
            }
            if (result.getSubmitMode() != null) {
                this.submitMode = result.getSubmitMode();
            }
            updateWorkerResult(workerResult);
        }
        this.updateTime = System.currentTimeMillis();
    }

    private void updateWorkerResult(WorkerResult workerResult) {
        if (workerResult == null) {
            return;
        }
        if (workerResult.getWorkerId() != null) {
            this.workerId = workerResult.getWorkerId();
        }
        if (workerResult.getAppId() != null) {
            this.appId = workerResult.getAppId();
        }
        if (workerResult.getOutputVars() != null) {
            this.outputVars = workerResult.getOutputVars();
        }
        if (workerResult.getWorkDirPath() != null) {
            this.workDirPath = workerResult.getWorkDirPath();
        }
        JsonNode resultJson = toResultJson(workerResult);
        if (resultJson != null) {
            this.result = resultJson;
        }
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
        updateWorkerResult(request.getWorkerResult());
        if (request.getPluginType() != null) {
            this.pluginType = request.getPluginType();
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
                .submitMode(submitMode)
                .workerResult(WorkerResult.builder()
                        .outputVars(outputVars)
                        .workerId(workerId)
                        .appId(appId)
                        .workDirPath(workDirPath)
                        .message(resultText("message"))
                        .pluginLogUri(resultText("pluginLogUri"))
                        .build())
                .build();
    }

    private String resultText(String fieldName) {
        return result == null || !result.hasNonNull(fieldName) ? null : result.get(fieldName).asText();
    }

    private JsonNode toResultJson(WorkerResult workerResult) {
        if (workerResult == null && result == null) {
            return null;
        }
        if (workerResult == null) {
            return result;
        }
        ObjectNode node = JacksonUtils.createObjectNode();
        if (workerResult.getMessage() != null) {
            node.put("message", workerResult.getMessage());
        }
        if (workerResult.getPluginLogUri() != null) {
            node.put("pluginLogUri", workerResult.getPluginLogUri());
        }
        return node.isEmpty() ? result : node;
    }

    private void fillWorkerResult(TaskRequest request) {
        WorkerResult workerResult = request.getWorkerResult();
        if (workerResult == null) {
            request.setWorkerResult(new WorkerResult());
            workerResult = request.getWorkerResult();
        }
        if (workerResult.getWorkerId() == null) {
            workerResult.setWorkerId(workerId);
        }
        if (workerResult.getAppId() == null) {
            workerResult.setAppId(appId);
        }
        if (workerResult.getWorkDirPath() == null) {
            workerResult.setWorkDirPath(workDirPath);
        }
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
        fillWorkerResult(request);
        if (request.getTaskState() == null) {
            request.setTaskState(taskState);
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
