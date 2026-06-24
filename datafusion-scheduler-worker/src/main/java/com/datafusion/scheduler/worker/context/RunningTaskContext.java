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
     * 任务提交快照.
     */
    private WorkerTaskExecutionSnap snapshot = new WorkerTaskExecutionSnap();

    /**
     * 任务运行态.
     */
    private WorkerTaskExecutionState executionState = new WorkerTaskExecutionState();

    /**
     * Worker 已接收并开始提交.
     */
    private boolean submitted;

    /**
     * 根据任务请求创建上下文.
     *
     * @param request 任务请求
     * @return 运行中任务上下文
     */
    public static RunningTaskContext fromRequest(TaskRequest request) {
        RunningTaskContext context = new RunningTaskContext();
        if (request == null) {
            return context;
        }
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
        return context;
    }

    /**
     * 根据持久化快照和运行态恢复上下文.
     *
     * @param snapshot 任务提交快照
     * @param state 任务运行态
     * @return 运行中任务上下文
     */
    public static RunningTaskContext fromSnapshotAndState(WorkerTaskExecutionSnap snapshot,
            WorkerTaskExecutionState state) {
        RunningTaskContext context = new RunningTaskContext();
        context.mergeSnapshot(snapshot, null);
        context.mergeState(state, null);
        return context;
    }

    /**
     * 更新最近结果.
     *
     * @param result 任务结果
     */
    public void updateResult(TaskResult result) {
        if (result != null) {
            WorkerResult workerResult = result.getWorkerResult();
            if (result.getTaskState() != null) {
                this.setTaskState(result.getTaskState());
            }
            if (result.getSubmitMode() != null) {
                this.setSubmitMode(result.getSubmitMode());
            }
            updateWorkerResult(workerResult);
        }
        setUpdateTime(System.currentTimeMillis());
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
            this.setFlowInstanceId(request.getFlowInstanceId());
        }
        if (request.getTaskName() != null) {
            this.setTaskName(request.getTaskName());
        }
        updateWorkerResult(request.getWorkerResult());
        if (request.getPluginType() != null) {
            this.setPluginType(request.getPluginType());
        }
        if (request.getTaskState() != null) {
            this.setTaskState(request.getTaskState());
        }
        if (request.getSubmitMode() != null) {
            this.setSubmitMode(request.getSubmitMode());
        }
        if (request.getTaskData() != null) {
            this.setTaskData(request.getTaskData());
        }
        if (request.getPluginParam() != null) {
            this.setPluginParam(request.getPluginParam());
        }
        setUpdateTime(System.currentTimeMillis());
    }

    /**
     * 合并任务提交快照.
     *
     * @param source 任务提交快照
     * @param workDirPath 任务运行目录
     */
    public void mergeSnapshot(WorkerTaskExecutionSnap source, String workDirPath) {
        if (source == null) {
            return;
        }
        setTaskInstanceId(firstText(getTaskInstanceId(), source.getTaskInstanceId()));
        setFlowInstanceId(firstText(getFlowInstanceId(), source.getFlowInstanceId()));
        setTaskName(firstText(getTaskName(), source.getTaskName()));
        setWorkerId(firstText(getWorkerId(), source.getWorkerId()));
        setPluginType(firstText(getPluginType(), source.getPluginType()));
        setRunMode(firstText(getRunMode(), source.getRunMode()));
        if (getSubmitMode() == null) {
            setSubmitMode(source.getSubmitMode());
        }
        if (getTaskData() == null) {
            setTaskData(source.getTaskData());
        }
        if (getPluginParam() == null) {
            setPluginParam(source.getPluginParam());
        }
        if (getWorkDirPath() == null) {
            setWorkDirPath(workDirPath);
        }
    }

    /**
     * 合并任务运行态.
     *
     * @param source 任务运行态
     * @param workDirPath 任务运行目录
     */
    public void mergeState(WorkerTaskExecutionState source, String workDirPath) {
        if (source == null) {
            return;
        }
        setTaskInstanceId(firstText(getTaskInstanceId(), source.getTaskInstanceId()));
        setWorkerId(firstText(getWorkerId(), source.getWorkerId()));
        setAppId(firstText(getAppId(), source.getAppId()));
        setWorkDirPath(firstText(getWorkDirPath(), source.getWorkDirPath()));
        if (source.getStatus() != null) {
            setTaskState(source.getStatus());
        }
        if (getResult() == null) {
            setResult(source.getResult());
        }
        if (getWorkDirPath() == null) {
            setWorkDirPath(workDirPath);
        }
        setUpdateTime(System.currentTimeMillis());
    }

    /**
     * 根据当前上下文构建任务结果.
     *
     * @return 任务结果
     */
    public TaskResult toTaskResult() {
        return TaskResult.builder()
                .taskInstanceId(getTaskInstanceId())
                .flowInstanceId(getFlowInstanceId())
                .taskName(getTaskName())
                .taskState(getTaskState())
                .submitMode(getSubmitMode())
                .workerResult(WorkerResult.builder()
                        .outputVars(getOutputVars())
                        .workerId(getWorkerId())
                        .appId(getAppId())
                        .workDirPath(getWorkDirPath())
                        .message(resultText("message"))
                        .pluginLogUri(resultText("pluginLogUri"))
                        .build())
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
            request.setFlowInstanceId(getFlowInstanceId());
        }
        if (request.getTaskName() == null) {
            request.setTaskName(getTaskName());
        }
        fillWorkerResult(request);
        if (request.getTaskState() == null) {
            request.setTaskState(getTaskState());
        }
        if (request.getPluginType() == null) {
            request.setPluginType(getPluginType());
        }
        if (request.getTaskData() == null) {
            request.setTaskData(getTaskData());
        }
        if (request.getPluginParam() == null) {
            request.setPluginParam(getPluginParam());
        }
        if (request.getSubmitMode() == null) {
            request.setSubmitMode(getSubmitMode());
        }
        return request;
    }

    /**
     * 获取任务提交快照.
     *
     * @return 任务提交快照
     */
    public WorkerTaskExecutionSnap getSnapshot() {
        return snapshot();
    }

    /**
     * 设置任务提交快照.
     *
     * @param snapshot 任务提交快照
     */
    public void setSnapshot(WorkerTaskExecutionSnap snapshot) {
        this.snapshot = snapshot == null ? new WorkerTaskExecutionSnap() : snapshot;
    }

    /**
     * 获取任务运行态.
     *
     * @return 任务运行态
     */
    public WorkerTaskExecutionState getExecutionState() {
        return executionState();
    }

    /**
     * 设置任务运行态.
     *
     * @param executionState 任务运行态
     */
    public void setExecutionState(WorkerTaskExecutionState executionState) {
        this.executionState = executionState == null ? new WorkerTaskExecutionState() : executionState;
    }

    /**
     * 获取流程实例 ID.
     *
     * @return 流程实例 ID
     */
    public String getFlowInstanceId() {
        return snapshot().getFlowInstanceId();
    }

    /**
     * 设置流程实例 ID.
     *
     * @param flowInstanceId 流程实例 ID
     */
    public void setFlowInstanceId(String flowInstanceId) {
        snapshot().setFlowInstanceId(flowInstanceId);
    }

    /**
     * 获取任务实例 ID.
     *
     * @return 任务实例 ID
     */
    public String getTaskInstanceId() {
        return firstText(executionState().getTaskInstanceId(), snapshot().getTaskInstanceId());
    }

    /**
     * 设置任务实例 ID.
     *
     * @param taskInstanceId 任务实例 ID
     */
    public void setTaskInstanceId(String taskInstanceId) {
        snapshot().setTaskInstanceId(taskInstanceId);
        executionState().setTaskInstanceId(taskInstanceId);
    }

    /**
     * 获取任务名称.
     *
     * @return 任务名称
     */
    public String getTaskName() {
        return snapshot().getTaskName();
    }

    /**
     * 设置任务名称.
     *
     * @param taskName 任务名称
     */
    public void setTaskName(String taskName) {
        snapshot().setTaskName(taskName);
    }

    /**
     * 获取插件类型.
     *
     * @return 插件类型
     */
    public String getPluginType() {
        return snapshot().getPluginType();
    }

    /**
     * 设置插件类型.
     *
     * @param pluginType 插件类型
     */
    public void setPluginType(String pluginType) {
        snapshot().setPluginType(pluginType);
    }

    /**
     * 获取运行模式.
     *
     * @return 运行模式
     */
    public String getRunMode() {
        return snapshot().getRunMode();
    }

    /**
     * 设置运行模式.
     *
     * @param runMode 运行模式
     */
    public void setRunMode(String runMode) {
        snapshot().setRunMode(runMode);
    }

    /**
     * 获取提交模式.
     *
     * @return 提交模式
     */
    public SubmitModeEnum getSubmitMode() {
        return snapshot().getSubmitMode();
    }

    /**
     * 设置提交模式.
     *
     * @param submitMode 提交模式
     */
    public void setSubmitMode(SubmitModeEnum submitMode) {
        snapshot().setSubmitMode(submitMode);
    }

    /**
     * 获取工作节点 ID.
     *
     * @return 工作节点 ID
     */
    public String getWorkerId() {
        return firstText(executionState().getWorkerId(), snapshot().getWorkerId());
    }

    /**
     * 设置工作节点 ID.
     *
     * @param workerId 工作节点 ID
     */
    public void setWorkerId(String workerId) {
        snapshot().setWorkerId(workerId);
        executionState().setWorkerId(workerId);
    }

    /**
     * 获取终端任务 ID.
     *
     * @return 终端任务 ID
     */
    public String getAppId() {
        return executionState().getAppId();
    }

    /**
     * 设置终端任务 ID.
     *
     * @param appId 终端任务 ID
     */
    public void setAppId(String appId) {
        executionState().setAppId(appId);
    }

    /**
     * 获取最近任务状态.
     *
     * @return 最近任务状态
     */
    public StatusEnum getTaskState() {
        return executionState().getStatus();
    }

    /**
     * 设置最近任务状态.
     *
     * @param taskState 最近任务状态
     */
    public void setTaskState(StatusEnum taskState) {
        executionState().setStatus(taskState);
    }

    /**
     * 获取渲染后的任务执行数据.
     *
     * @return 渲染后的任务执行数据
     */
    public JsonNode getTaskData() {
        return snapshot().getTaskData();
    }

    /**
     * 设置渲染后的任务执行数据.
     *
     * @param taskData 渲染后的任务执行数据
     */
    public void setTaskData(JsonNode taskData) {
        snapshot().setTaskData(taskData);
    }

    /**
     * 获取插件参数.
     *
     * @return 插件参数
     */
    public JsonNode getPluginParam() {
        return snapshot().getPluginParam();
    }

    /**
     * 设置插件参数.
     *
     * @param pluginParam 插件参数
     */
    public void setPluginParam(JsonNode pluginParam) {
        snapshot().setPluginParam(pluginParam);
    }

    /**
     * 获取任务运行目录路径.
     *
     * @return 任务运行目录路径
     */
    public String getWorkDirPath() {
        return executionState().getWorkDirPath();
    }

    /**
     * 设置任务运行目录路径.
     *
     * @param workDirPath 任务运行目录路径
     */
    public void setWorkDirPath(String workDirPath) {
        executionState().setWorkDirPath(workDirPath);
    }

    /**
     * 获取执行结果说明.
     *
     * @return 执行结果说明
     */
    public JsonNode getResult() {
        return executionState().getResult();
    }

    /**
     * 设置执行结果说明.
     *
     * @param result 执行结果说明
     */
    public void setResult(JsonNode result) {
        executionState().setResult(result);
    }

    /**
     * 获取输出变量列表.
     *
     * @return 输出变量列表
     */
    public Map<String, Variable> getOutputVars() {
        return executionState().getOutputVars();
    }

    /**
     * 设置输出变量列表.
     *
     * @param outputVars 输出变量列表
     */
    public void setOutputVars(Map<String, Variable> outputVars) {
        executionState().setOutputVars(outputVars);
    }

    /**
     * 获取状态更新时间.
     *
     * @return 状态更新时间
     */
    public Long getUpdateTime() {
        return executionState().getUpdateTime();
    }

    /**
     * 设置状态更新时间.
     *
     * @param updateTime 状态更新时间
     */
    public void setUpdateTime(Long updateTime) {
        executionState().setUpdateTime(updateTime);
    }

    /**
     * 判断任务是否已经提交.
     *
     * @return 是否已经提交
     */
    public boolean isSubmitted() {
        return submitted || getAppId() != null || getWorkDirPath() != null || getResult() != null
                || isSubmittedState(getTaskState());
    }

    /**
     * 标记任务已进入 Worker 提交流程.
     */
    public void markSubmitted() {
        this.submitted = true;
    }

    private boolean isSubmittedState(StatusEnum state) {
        return state == StatusEnum.RUNNING || state == StatusEnum.STOPPING || state == StatusEnum.KILLING
                || state == StatusEnum.UNKNOWN || state != null && state.isFinalState();
    }

    private void updateWorkerResult(WorkerResult workerResult) {
        if (workerResult == null) {
            return;
        }
        if (workerResult.getWorkerId() != null) {
            this.setWorkerId(workerResult.getWorkerId());
        }
        if (workerResult.getAppId() != null) {
            this.setAppId(workerResult.getAppId());
        }
        if (workerResult.getOutputVars() != null) {
            this.setOutputVars(workerResult.getOutputVars());
        }
        if (workerResult.getWorkDirPath() != null) {
            this.setWorkDirPath(workerResult.getWorkDirPath());
        }
        JsonNode resultJson = toResultJson(workerResult);
        if (resultJson != null) {
            this.setResult(resultJson);
        }
    }

    private WorkerTaskExecutionSnap snapshot() {
        if (snapshot == null) {
            snapshot = new WorkerTaskExecutionSnap();
        }
        return snapshot;
    }

    private WorkerTaskExecutionState executionState() {
        if (executionState == null) {
            executionState = new WorkerTaskExecutionState();
        }
        return executionState;
    }

    private void fillWorkerResult(TaskRequest request) {
        WorkerResult workerResult = request.getWorkerResult();
        if (workerResult == null) {
            request.setWorkerResult(new WorkerResult());
            workerResult = request.getWorkerResult();
        }
        if (workerResult.getWorkerId() == null) {
            workerResult.setWorkerId(getWorkerId());
        }
        if (workerResult.getAppId() == null) {
            workerResult.setAppId(getAppId());
        }
        if (workerResult.getWorkDirPath() == null) {
            workerResult.setWorkDirPath(getWorkDirPath());
        }
    }

    private String resultText(String fieldName) {
        JsonNode result = getResult();
        return result == null || !result.hasNonNull(fieldName) ? null : result.get(fieldName).asText();
    }

    private JsonNode toResultJson(WorkerResult workerResult) {
        if (workerResult == null && getResult() == null) {
            return null;
        }
        if (workerResult == null) {
            return getResult();
        }
        ObjectNode node = JacksonUtils.createObjectNode();
        if (workerResult.getMessage() != null) {
            node.put("message", workerResult.getMessage());
        }
        if (workerResult.getPluginLogUri() != null) {
            node.put("pluginLogUri", workerResult.getPluginLogUri());
        }
        return node.isEmpty() ? getResult() : node;
    }

    private String firstText(String left, String right) {
        return left == null ? right : left;
    }
}
