package com.datafusion.agent.runtime.worker.context;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;
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
    private final WorkerTaskExecutionStateStore stateStore;

    /**
     * 构造函数.
     *
     * @param stateStore 任务执行状态存储
     */
    public AgentWorkerTaskContextStorage(WorkerTaskExecutionStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        String key = contextKey(taskInstanceId);
        RunningTaskContext context = contextMap.get(key);
        if (context != null) {
            return context;
        }
        return stateStore.read(taskInstanceId)
                .map(this::toContext)
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
        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .flowInstanceId(context.getFlowInstanceId())
                .taskInstanceId(context.getTaskInstanceId())
                .pluginType(context.getPluginType())
                .runMode(resolveRunMode(context))
                .appId(context.getAppId())
                .logPath(context.getLogPath())
                .status(context.getTaskState())
                .taskData(context.getTaskData())
                .pluginParam(context.getPluginParam())
                .result(context.getResult())
                .build();
        mergeExistingState(state);
        if (shouldRecord(context, state)) {
            stateStore.record(state);
        }
    }

    private void mergeExistingState(WorkerTaskExecutionState state) {
        stateStore.read(state.getTaskInstanceId()).ifPresent(existing -> {
            if (state.getFlowInstanceId() == null) {
                state.setFlowInstanceId(existing.getFlowInstanceId());
            }
            if (state.getPluginType() == null) {
                state.setPluginType(existing.getPluginType());
            }
            if (state.getRunMode() == null) {
                state.setRunMode(existing.getRunMode());
            }
            if (state.getAppId() == null) {
                state.setAppId(existing.getAppId());
            }
            if (state.getLogPath() == null) {
                state.setLogPath(existing.getLogPath());
            }
            if (state.getWorkId() == null) {
                state.setWorkId(existing.getWorkId());
            }
            if (state.getExitCode() == null) {
                state.setExitCode(existing.getExitCode());
            }
            if (state.getPluginParam() == null) {
                state.setPluginParam(existing.getPluginParam());
            } else if (existing.getPluginParam() != null && existing.getPluginParam().isObject()
                    && state.getPluginParam().isObject() && existing.getPluginParam().hasNonNull("_runtime")
                    && !state.getPluginParam().hasNonNull("_runtime")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) state.getPluginParam())
                        .set("_runtime", existing.getPluginParam().get("_runtime"));
            }
        });
    }

    private boolean shouldRecord(RunningTaskContext context, WorkerTaskExecutionState state) {
        String key = contextKey(context.getTaskInstanceId());
        String sign = stateSign(state);
        return !sign.equals(stateSignMap.put(key, sign));
    }

    private String stateSign(WorkerTaskExecutionState state) {
        return safeText(state.getFlowInstanceId()) + '|'
                + safeText(state.getTaskInstanceId()) + '|'
                + safeText(state.getPluginType()) + '|'
                + safeText(state.getRunMode()) + '|'
                + safeText(state.getAppId()) + '|'
                + safeText(state.getLogPath()) + '|'
                + safeText(state.getWorkId()) + '|'
                + (state.getStatus() == null ? "" : state.getStatus().name()) + '|'
                + jsonText(state.getResult()) + '|'
                + String.valueOf(state.getTaskData()) + '|'
                + String.valueOf(state.getPluginParam());
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String jsonText(JsonNode value) {
        return value == null ? "" : value.toString();
    }

    private RunningTaskContext toContext(WorkerTaskExecutionState state) {
        RunningTaskContext context = new RunningTaskContext();
        context.setFlowInstanceId(state.getFlowInstanceId());
        context.setTaskInstanceId(state.getTaskInstanceId());
        context.setPluginType(state.getPluginType());
        context.setRunMode(state.getRunMode());
        context.setAppId(state.getAppId());
        context.setLogPath(state.getLogPath());
        context.setTaskState(state.getStatus());
        context.setTaskData(state.getTaskData());
        context.setPluginParam(state.getPluginParam());
        context.setResult(state.getResult());
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
