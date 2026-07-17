package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Worker 任务持久化模型到 RPC 结果的转换器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public final class WorkerTaskResultMapper {

    private WorkerTaskResultMapper() {
    }

    /**
     * 从提交快照和运行态构造任务结果.
     *
     * @param snapshot 任务提交快照
     * @param state    任务运行态
     * @return 任务结果
     */
    public static TaskResult toTaskResult(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return toTaskResult(snapshot, state, null);
    }

    /**
     * 从提交快照和运行态构造任务结果，并按需覆盖响应说明.
     *
     * @param snapshot 任务提交快照
     * @param state    任务运行态
     * @param message  响应说明，为空时使用运行态 result.message
     * @return 任务结果
     */
    public static TaskResult toTaskResult(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state,
            String message) {
        if (snapshot == null || state == null) {
            throw new IllegalArgumentException("snapshot和state不能为空");
        }
        JsonNode executionResult = state.getResult();
        String stateMessage = text(executionResult, "message");
        return TaskResult.builder()
                .flowInstanceId(snapshot.getFlowInstanceId())
                .taskInstanceId(snapshot.getTaskInstanceId())
                .taskName(snapshot.getTaskName())
                .taskState(state.getStatus() == null ? StatusEnum.UNKNOWN : state.getStatus())
                .submitMode(snapshot.getSubmitMode())
                .workerResult(WorkerResult.builder()
                        .workerId(state.getWorkerId() == null ? snapshot.getWorkerId() : state.getWorkerId())
                        .appId(state.getAppId())
                        .workDirPath(state.getWorkDirPath())
                        .message(isBlank(message) ? stateMessage : message)
                        .pluginLogUri(text(executionResult, "pluginLogUri"))
                        .outputVars(state.getOutputVars())
                        .build())
                .build();
    }

    private static String text(JsonNode result, String fieldName) {
        return result == null || !result.hasNonNull(fieldName) ? null : result.get(fieldName).asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
