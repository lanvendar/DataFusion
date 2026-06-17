package com.datafusion.agent.runtime.worker.context;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时 worker 任务上下文存储.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
public class AgentWorkerTaskContextStorage implements WorkerTaskContextStorage {

    /**
     * 运行中任务上下文.
     */
    private final Map<String, RunningTaskContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 最近一次状态签名.
     */
    private final Map<String, String> stateSignMap = new ConcurrentHashMap<>();

    /**
     * 任务执行状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 构造函数.
     *
     * @param stateStore 任务执行状态存储
     */
    public AgentWorkerTaskContextStorage(WorkerTaskExecutionStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        String key = contextKey(taskInstanceId);
        RunningTaskContext context = contextMap.get(key);
        if (context != null) {
            return context;
        }
        return stateStore.readState(taskInstanceId)
                .map(state -> toContext(stateStore.readSnapshot(taskInstanceId).orElse(null), state))
                .map(restored -> {
                    contextMap.put(key, restored);
                    return restored;
                })
                .orElse(null);
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
        stateSignMap.remove(key);
        stateStore.remove(taskInstanceId);
    }

    private static String contextKey(String taskInstanceId) {
        return taskInstanceId;
    }

    private void recordContext(RunningTaskContext context) {
        WorkerTaskExecutionSnap snapshot = WorkerTaskExecutionSnap.builder()
                .flowInstanceId(context.getFlowInstanceId())
                .taskInstanceId(context.getTaskInstanceId())
                .taskName(context.getTaskName())
                .pluginType(context.getPluginType())
                .runMode(resolveRunMode(context))
                .taskData(context.getTaskData())
                .pluginParam(context.getPluginParam())
                .build();
        if (stateStore.readSnapshot(context.getTaskInstanceId()).isEmpty()) {
            stateStore.saveSnapshot(snapshot);
        }

        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .taskInstanceId(context.getTaskInstanceId())
                .appId(context.getAppId())
                .logPath(context.getLogPath())
                .status(context.getTaskState())
                .result(context.getResult())
                .build();
        mergeExistingState(state);
        if (shouldRecord(context, state)) {
            stateStore.saveState(state);
        }
    }

    private void mergeExistingState(WorkerTaskExecutionState state) {
        stateStore.readState(state.getTaskInstanceId()).ifPresent(existing -> {
            if (state.getAppId() == null) {
                state.setAppId(existing.getAppId());
            }
            if (state.getLogPath() == null) {
                state.setLogPath(existing.getLogPath());
            }
            if (state.getExitCode() == null) {
                state.setExitCode(existing.getExitCode());
            }
        });
    }

    private boolean shouldRecord(RunningTaskContext context, WorkerTaskExecutionState state) {
        String key = contextKey(context.getTaskInstanceId());
        String sign = stateSign(state);
        return !sign.equals(stateSignMap.put(key, sign));
    }

    private String stateSign(WorkerTaskExecutionState state) {
        return safeText(state.getTaskInstanceId()) + '|'
                + safeText(state.getAppId()) + '|'
                + safeText(state.getLogPath()) + '|'
                + (state.getStatus() == null ? "" : state.getStatus().name()) + '|'
                + String.valueOf(state.getExitCode()) + '|'
                + jsonText(state.getResult());
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String jsonText(JsonNode value) {
        return value == null ? "" : value.toString();
    }

    private RunningTaskContext toContext(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        RunningTaskContext context = new RunningTaskContext();
        context.setTaskInstanceId(state.getTaskInstanceId());
        context.setAppId(state.getAppId());
        context.setLogPath(state.getLogPath());
        context.setTaskState(state.getStatus());
        context.setResult(state.getResult());
        if (snapshot != null) {
            context.setFlowInstanceId(snapshot.getFlowInstanceId());
            context.setTaskName(snapshot.getTaskName());
            context.setPluginType(snapshot.getPluginType());
            context.setRunMode(snapshot.getRunMode());
            context.setTaskData(snapshot.getTaskData());
            context.setPluginParam(snapshot.getPluginParam());
        }
        context.setSubmitted(true);
        context.setCreateTime(System.currentTimeMillis());
        context.setUpdateTime(System.currentTimeMillis());
        return context;
    }

    private String resolveRunMode(RunningTaskContext context) {
        if (context.getRunMode() != null) {
            return context.getRunMode();
        }
        if (context.getPluginParam() != null && context.getPluginParam().hasNonNull("runMode")) {
            String runMode = context.getPluginParam().get("runMode").asText();
            context.setRunMode(runMode);
            return runMode;
        }
        return null;
    }
}
