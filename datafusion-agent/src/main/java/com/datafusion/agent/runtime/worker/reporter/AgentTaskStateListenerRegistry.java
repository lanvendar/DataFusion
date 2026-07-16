package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent 任务状态监听注册器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/16
 * @since 1.0.0
 */
@Slf4j
public class AgentTaskStateListenerRegistry {

    /**
     * 任务执行状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 任务结果上报器.
     */
    private final TaskResultReporter resultReporter;

    /**
     * 任务监听调度器.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 插件运行模式状态映射.
     */
    private final Map<String, PluginRunModeStateMapping> stateMappings;

    /**
     * 任务监听注册项.
     */
    private final Map<String, TaskStateListener> listeners = new ConcurrentHashMap<>();

    /**
     * 查询失败计数.
     */
    private final Map<String, Integer> queryFailureCounts = new ConcurrentHashMap<>();

    /**
     * 刷新间隔.
     */
    private final long refreshIntervalMs;

    /**
     * UNKNOWN 推进阈值.
     */
    private final int unknownThreshold;

    /**
     * 终态监听保留时间.
     */
    private final long listenerRetentionMs;

    /**
     * 终态监听最大保留数量.
     */
    private final int listenerRetentionNum;

    /**
     * 构造函数.
     *
     * @param stateStore           任务执行状态存储
     * @param resultReporter       任务结果上报器
     * @param scheduler            任务监听调度器
     * @param mappings             插件运行模式状态映射
     * @param refreshIntervalMs    刷新间隔
     * @param unknownThreshold     UNKNOWN 推进阈值
     * @param listenerRetentionMs  终态监听保留时间
     * @param listenerRetentionNum 终态监听最大保留数量
     */
    public AgentTaskStateListenerRegistry(WorkerTaskExecutionStore stateStore, TaskResultReporter resultReporter,
            ScheduledExecutorService scheduler, List<PluginRunModeStateMapping> mappings, long refreshIntervalMs,
            int unknownThreshold, long listenerRetentionMs, int listenerRetentionNum) {
        this.stateStore = stateStore;
        this.resultReporter = resultReporter;
        this.scheduler = scheduler;
        this.refreshIntervalMs = Math.max(refreshIntervalMs, 1000L);
        this.unknownThreshold = Math.max(unknownThreshold, 1);
        this.listenerRetentionMs = Math.max(listenerRetentionMs, 0L);
        this.listenerRetentionNum = Math.max(listenerRetentionNum, 0);
        Map<String, PluginRunModeStateMapping> mappingsByRoute = new LinkedHashMap<>();
        for (PluginRunModeStateMapping mapping : mappings == null ? Collections.<PluginRunModeStateMapping>emptyList()
                : mappings) {
            if (mapping == null) {
                continue;
            }
            if (isBlank(mapping.pluginType()) || isBlank(mapping.runMode())) {
                throw new IllegalArgumentException("状态映射的pluginType和runMode不能为空");
            }
            String route = mappingKey(mapping.pluginType(), mapping.runMode());
            if (mappingsByRoute.putIfAbsent(route, mapping) != null) {
                throw new IllegalArgumentException("重复的插件状态映射: " + route);
            }
        }
        this.stateMappings = Collections.unmodifiableMap(mappingsByRoute);
    }

    /**
     * 注册任务状态监听.
     *
     * @param taskInstanceId 任务实例 ID
     */
    public void register(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        stateStore.withTaskLock(taskInstanceId, () -> {
            StatusEnum status = stateStore.readState(taskInstanceId)
                    .map(WorkerTaskExecutionState::getStatus)
                    .orElse(null);
            registerTask(taskInstanceId, status, false);
            return null;
        });
    }

    /**
     * 注销任务状态监听.
     *
     * @param taskInstanceId 任务实例 ID
     */
    public void unregister(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        stateStore.withTaskLock(taskInstanceId, () -> {
            TaskStateListener listener = listeners.remove(taskInstanceId);
            if (listener != null) {
                listener.future.cancel(false);
                queryFailureCounts.remove(taskInstanceId);
                log.info("注销任务状态监听, taskInstanceId={}", taskInstanceId);
            }
            return null;
        });
    }

    /**
     * 恢复 Manager 返回的未完成任务.
     *
     * @param requests 任务请求清单
     */
    public void restoreTasks(List<TaskRequest> requests) {
        List<TaskRequest> tasks = requests == null ? Collections.emptyList() : requests;
        stateStore.restoreListeningTasks(tasks);
        int restoredCount = 0;
        for (TaskRequest task : tasks) {
            if (task == null || isBlank(task.getTaskInstanceId())) {
                continue;
            }
            boolean restored = stateStore.withTaskLock(task.getTaskInstanceId(), () -> {
                WorkerTaskExecutionState state = stateStore.readState(task.getTaskInstanceId()).orElse(null);
                if (state == null || state.getStatus() == null) {
                    return false;
                }
                registerTask(task.getTaskInstanceId(), state.getStatus(), true);
                return true;
            });
            restoredCount += restored ? 1 : 0;
        }
        log.info("恢复agent任务状态监听, taskCount={}", restoredCount);
    }

    void refreshTask(String taskInstanceId) {
        TaskStateListener listener = listeners.get(taskInstanceId);
        if (listener == null) {
            return;
        }
        try {
            WorkerTaskExecutionState queriedState = stateStore.readState(taskInstanceId).orElse(null);
            if (queriedState == null || queriedState.getStatus() == null) {
                log.warn("任务运行态不存在, 注销状态监听, taskInstanceId={}", taskInstanceId);
                unregister(taskInstanceId);
                return;
            }
            WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(taskInstanceId).orElse(null);
            PluginRunModeStateMapping mapping = stateMapping(snapshot);
            StatusEnum queriedStatus = queriedState.getStatus();
            long queriedRevision = queriedState.getRevision();
            if (!queriedStatus.isFinalState()) {
                listener.terminalSince = 0L;
                if (mapping == null) {
                    log.warn("未匹配到插件运行模式状态映射, taskInstanceId={}, pluginType={}, runMode={}",
                            taskInstanceId, snapshot == null ? null : snapshot.getPluginType(),
                            snapshot == null ? null : snapshot.getRunMode());
                    return;
                }
                if (isWaitingForRuntimeRef(queriedState)) {
                    return;
                }
            }
            StatusEnum mappedStatus = queriedStatus.isFinalState() ? queriedStatus : mapping.mapState(queriedState);
            commit(listener, snapshot, mapping, queriedStatus, queriedRevision, mappedStatus);
        } catch (Exception e) {
            log.warn("刷新任务状态失败, taskInstanceId={}", taskInstanceId, e);
        }
        evictTerminalListeners();
    }

    int listenerCount() {
        return listeners.size();
    }

    boolean isRegistered(String taskInstanceId) {
        return listeners.containsKey(taskInstanceId);
    }

    private void registerTask(String taskInstanceId, StatusEnum observedStatus, boolean reportPending) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        listeners.compute(taskInstanceId, (key, current) -> {
            if (current != null) {
                current.observedStatus = observedStatus;
                current.reportPending = current.reportPending || reportPending;
                return current;
            }
            TaskStateListener listener = new TaskStateListener(taskInstanceId, observedStatus, reportPending);
            listener.future = scheduler.scheduleWithFixedDelay(() -> refreshTask(taskInstanceId), refreshIntervalMs,
                    refreshIntervalMs, TimeUnit.MILLISECONDS);
            log.info("注册任务状态监听, taskInstanceId={}, intervalMs={}, reportPending={}",
                    taskInstanceId, refreshIntervalMs, reportPending);
            return listener;
        });
    }

    private void commit(TaskStateListener listener, WorkerTaskExecutionSnap snapshot,
            PluginRunModeStateMapping mapping, StatusEnum queriedStatus, long queriedRevision,
            StatusEnum mappedStatus) {
        stateStore.withTaskLock(listener.taskInstanceId, () -> {
            if (listeners.get(listener.taskInstanceId) != listener) {
                return null;
            }
            WorkerTaskExecutionState state = stateStore.readState(listener.taskInstanceId).orElse(null);
            if (state == null || state.getStatus() != queriedStatus || state.getRevision() != queriedRevision) {
                log.info("任务运行态已变化, 丢弃本次刷新结果, taskInstanceId={}, queriedStatus={},"
                                + " latestStatus={}, queriedRevision={}, latestRevision={}",
                        listener.taskInstanceId, queriedStatus, state == null ? null : state.getStatus(),
                        queriedRevision, state == null ? null : state.getRevision());
                return null;
            }
            StatusEnum nextStatus = queriedStatus.isFinalState() ? queriedStatus : normalizeUnknown(state, mappedStatus);
            if (nextStatus == null) {
                return null;
            }
            if (listener.observedStatus != queriedStatus) {
                listener.reportPending = true;
                listener.observedStatus = queriedStatus;
            }
            if (nextStatus != queriedStatus) {
                if (!canApplyMappedState(queriedStatus, nextStatus)) {
                    log.warn("拒绝插件状态覆盖当前任务状态, taskInstanceId={}, currentStatus={}, mappedStatus={}",
                            listener.taskInstanceId, queriedStatus, nextStatus);
                    return null;
                }
                state.setStatus(nextStatus);
                stateStore.saveState(state);
                state = stateStore.readState(listener.taskInstanceId).orElse(null);
                if (state == null || state.getStatus() != nextStatus) {
                    log.warn("任务状态写入失败, 暂不上报, taskInstanceId={}, expectedStatus={}",
                            listener.taskInstanceId, nextStatus);
                    return null;
                }
                listener.observedStatus = state.getStatus();
                listener.reportPending = true;
                log.info("提交任务状态变化, taskInstanceId={}, oldStatus={}, newStatus={}",
                        listener.taskInstanceId, queriedStatus, nextStatus);
            }
            if (!listener.reportPending) {
                retainIfFinal(listener, state.getStatus());
                return null;
            }
            listener.terminalSince = 0L;
            if (state.getStatus().isFinalState() && mapping != null) {
                mapping.beforeFinalReport(state);
            }
            TaskResult result = toTaskResult(snapshot, state);
            boolean reported = resultReporter.report(result);
            if (!reported) {
                log.warn("任务状态上报失败, 等待下次监听重试, taskInstanceId={}, status={}",
                        listener.taskInstanceId, state.getStatus());
                return null;
            }
            listener.reportPending = false;
            retainIfFinal(listener, state.getStatus());
            log.info("任务状态上报成功, taskInstanceId={}, status={}", listener.taskInstanceId, state.getStatus());
            return null;
        });
    }

    private void retainIfFinal(TaskStateListener listener, StatusEnum status) {
        if (status != null && status.isFinalState()) {
            if (listener.terminalSince == 0L) {
                listener.terminalSince = System.currentTimeMillis();
            }
            queryFailureCounts.remove(listener.taskInstanceId);
        } else {
            listener.terminalSince = 0L;
        }
    }

    private void evictTerminalListeners() {
        long now = System.currentTimeMillis();
        List<TaskStateListener> terminalListeners = listeners.values()
                .stream()
                .filter(listener -> listener.terminalSince > 0L && !listener.reportPending)
                .sorted(Comparator.comparingLong(listener -> listener.terminalSince))
                .toList();
        Set<String> expiredTaskIds = new LinkedHashSet<>();
        terminalListeners.stream()
                .filter(listener -> now - listener.terminalSince >= listenerRetentionMs)
                .map(listener -> listener.taskInstanceId)
                .forEach(expiredTaskIds::add);
        List<TaskStateListener> retainedListeners = new ArrayList<>(terminalListeners);
        retainedListeners.removeIf(listener -> expiredTaskIds.contains(listener.taskInstanceId));
        int overflow = Math.max(retainedListeners.size() - listenerRetentionNum, 0);
        retainedListeners.stream()
                .limit(overflow)
                .map(listener -> listener.taskInstanceId)
                .forEach(expiredTaskIds::add);
        expiredTaskIds.forEach(this::evictTerminalListener);
    }

    private void evictTerminalListener(String taskInstanceId) {
        stateStore.withTaskLock(taskInstanceId, () -> {
            TaskStateListener listener = listeners.get(taskInstanceId);
            WorkerTaskExecutionState state = stateStore.readState(taskInstanceId).orElse(null);
            if (listener == null || listener.reportPending || listener.terminalSince == 0L || state == null
                    || state.getStatus() == null || !state.getStatus().isFinalState()) {
                if (listener != null) {
                    listener.terminalSince = 0L;
                }
                return null;
            }
            listeners.remove(taskInstanceId);
            listener.future.cancel(false);
            queryFailureCounts.remove(taskInstanceId);
            log.info("清理终态任务监听, taskInstanceId={}, status={}", taskInstanceId, state.getStatus());
            return null;
        });
    }

    private StatusEnum normalizeUnknown(WorkerTaskExecutionState state, StatusEnum mappedStatus) {
        if (mappedStatus != StatusEnum.UNKNOWN) {
            queryFailureCounts.remove(state.getTaskInstanceId());
            return mappedStatus;
        }
        int failureCount = queryFailureCounts.merge(state.getTaskInstanceId(), 1, Integer::sum);
        return failureCount < unknownThreshold ? state.getStatus() : StatusEnum.UNKNOWN;
    }

    private boolean canApplyMappedState(StatusEnum currentStatus, StatusEnum mappedStatus) {
        return switch (currentStatus) {
            case SUBMITTING, SUBMIT_SUCCESS -> mappedStatus == StatusEnum.RUNNING
                    || mappedStatus == StatusEnum.RUN_SUCCESS || mappedStatus == StatusEnum.SUBMIT_FAILURE
                    || mappedStatus == StatusEnum.RUN_FAILURE || mappedStatus == StatusEnum.UNKNOWN;
            case RUNNING -> mappedStatus == StatusEnum.RUN_SUCCESS || mappedStatus == StatusEnum.RUN_FAILURE
                    || mappedStatus == StatusEnum.UNKNOWN;
            case STOPPING -> mappedStatus == StatusEnum.STOP_SUCCESS || mappedStatus == StatusEnum.STOP_FAILURE
                    || mappedStatus == StatusEnum.UNKNOWN;
            case KILLING -> mappedStatus == StatusEnum.KILLED || mappedStatus == StatusEnum.UNKNOWN;
            default -> false;
        };
    }

    private PluginRunModeStateMapping stateMapping(WorkerTaskExecutionSnap snapshot) {
        if (snapshot == null || isBlank(snapshot.getPluginType()) || isBlank(snapshot.getRunMode())) {
            return null;
        }
        return stateMappings.get(mappingKey(snapshot.getPluginType(), snapshot.getRunMode()));
    }

    private TaskResult toTaskResult(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return TaskResult.builder()
                .taskInstanceId(state.getTaskInstanceId())
                .flowInstanceId(snapshot == null ? null : snapshot.getFlowInstanceId())
                .taskName(snapshot == null ? null : snapshot.getTaskName())
                .taskState(state.getStatus())
                .workerResult(WorkerResult.builder()
                        .outputVars(state.getOutputVars())
                        .workerId(state.getWorkerId() == null && snapshot != null ? snapshot.getWorkerId() : state.getWorkerId())
                        .appId(state.getAppId())
                        .workDirPath(state.getWorkDirPath())
                        .message(resultText(state.getResult(), "message"))
                        .pluginLogUri(resultText(state.getResult(), "pluginLogUri"))
                        .build())
                .build();
    }

    private String resultText(JsonNode result, String fieldName) {
        return result == null || !result.hasNonNull(fieldName) ? null : result.get(fieldName).asText();
    }

    private boolean isWaitingForRuntimeRef(WorkerTaskExecutionState state) {
        return isBlank(state.getAppId()) && isBlank(state.getWorkDirPath())
                && (state.getStatus() == StatusEnum.SUBMITTING || state.getStatus() == StatusEnum.SUBMIT_SUCCESS);
    }

    private static String mappingKey(String pluginType, String runMode) {
        return normalize(pluginType) + ':' + normalize(runMode);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 单任务监听注册项.
     */
    private static final class TaskStateListener {

        /**
         * 任务实例 ID.
         */
        private final String taskInstanceId;

        /**
         * 周期监听句柄.
         */
        private ScheduledFuture<?> future;

        /**
         * 最近一次已观察的本地状态.
         */
        private volatile StatusEnum observedStatus;

        /**
         * 是否存在待重试上报.
         */
        private volatile boolean reportPending;

        /**
         * 首次确认终态时间.
         */
        private volatile long terminalSince;

        private TaskStateListener(String taskInstanceId, StatusEnum observedStatus, boolean reportPending) {
            this.taskInstanceId = taskInstanceId;
            this.observedStatus = observedStatus;
            this.reportPending = reportPending;
        }
    }
}
