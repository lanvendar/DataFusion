package com.datafusion.agent.runtime.task;

import com.datafusion.agent.runtime.AgentExecutionStatusRecord;
import com.datafusion.agent.runtime.AgentExecutionStatusRecorder;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时 worker 任务上下文存储.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/3
 * @since 1.0.0
 */
public class AgentWorkerTaskContextStorage implements WorkerTaskContextStorage {

    /**
     * 运行中任务上下文.
     */
    private final Map<String, RunningTaskContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 最近一次记录签名.
     */
    private final Map<String, String> recordSignMap = new ConcurrentHashMap<>();

    /**
     * 执行状态记录器.
     */
    private final AgentExecutionStatusRecorder statusRecorder;

    /**
     * 构造函数.
     *
     * @param statusRecorder 执行状态记录器
     */
    public AgentWorkerTaskContextStorage(AgentExecutionStatusRecorder statusRecorder) {
        this.statusRecorder = statusRecorder;
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        return contextMap.get(contextKey(taskInstanceId));
    }

    @Override
    public RunningTaskContext getOrCreate(TaskRequest request) {
        return contextMap.computeIfAbsent(contextKey(request.getTaskInstanceId()),
                key -> RunningTaskContext.fromRequest(request));
    }

    @Override
    public void save(RunningTaskContext context) {
        if (context == null) {
            return;
        }
        contextMap.put(contextKey(context.getTaskInstanceId()), context);
        recordContext(context);
    }

    @Override
    public void remove(String taskInstanceId) {
        String key = contextKey(taskInstanceId);
        contextMap.remove(key);
        recordSignMap.remove(key);
    }

    private static String contextKey(String taskInstanceId) {
        return taskInstanceId;
    }

    private void recordContext(RunningTaskContext context) {
        if (context == null) {
            return;
        }
        AgentExecutionStatusRecord record = AgentExecutionStatusRecord.builder()
                .flowInstanceId(context.getFlowInstanceId())
                .executionId(context.getTaskInstanceId())
                .appId(context.getAppId())
                .status(statusName(context.getTaskState()))
                .result(context.getLastResult() == null ? null : context.getLastResult().getResult())
                .build();
        if (shouldRecord(context, record)) {
            statusRecorder.record(record);
        }
    }

    private String statusName(StatusEnum status) {
        return status == null ? "unknown" : status.name().toLowerCase(Locale.ROOT);
    }

    private boolean shouldRecord(RunningTaskContext context, AgentExecutionStatusRecord record) {
        String key = contextKey(context.getTaskInstanceId());
        String sign = recordSign(record);
        return !sign.equals(recordSignMap.put(key, sign));
    }

    private String recordSign(AgentExecutionStatusRecord record) {
        return safeText(record.getFlowInstanceId()) + '|'
                + safeText(record.getExecutionId()) + '|'
                + safeText(record.getAppId()) + '|'
                + safeText(record.getWorkId()) + '|'
                + safeText(record.getStatus()) + '|'
                + safeText(record.getResult());
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
