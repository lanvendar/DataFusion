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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent 任务状态监听注册器.
 *
 * <p>每个任务只注册一个周期监听。刷新过程先在锁外查询并映射第三方状态，只有状态变化、存在待上报事件或需要处理终态保留时，
 * 才进入任务锁完成 {@code status + revision} 校验和本地提交；Manager 上报始终在任务锁外执行。
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
     * 任务监听注册项，包含活跃监听和处于终态保留期的监听.
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
            TaskStateListener listener;
            synchronized (listeners) {
                listener = listeners.remove(taskInstanceId);
            }
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

    /**
     * 执行单个任务的一次状态刷新.
     *
     * <p>第三方查询前无锁读取查询基线 Q，查询后再次无锁读取最新状态 S0。Q 与 S0 的 revision 不一致时直接丢弃查询结果；
     * 映射状态与 S0 一致且没有待上报事件时也直接结束。只有状态可能变化或需要补偿上报时，才由
     * {@link #commitAndReport(TaskStateListener, WorkerTaskExecutionSnap, PluginRunModeStateMapping,
     * StatusEnum, long, StatusEnum)} 在锁内复读 revision，避免第三方查询期间发生的 stop、kill 或 submit 写入被覆盖。
     *
     * @param taskInstanceId 任务实例 ID
     */
    void refreshTask(String taskInstanceId) {
        TaskStateListener listener = listeners.get(taskInstanceId);
        if (listener == null) {
            return;
        }
        try {
            // 1. 无锁读取第三方查询基线 Q；status + revision 共同描述本次查询基于的本地运行态。
            WorkerTaskExecutionState queryBaseline = stateStore.readState(taskInstanceId).orElse(null);
            if (queryBaseline == null || queryBaseline.getStatus() == null) {
                log.warn("任务运行态不存在, 注销状态监听, taskInstanceId={}", taskInstanceId);
                unregister(taskInstanceId);
                return;
            }
            WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(taskInstanceId).orElse(null);
            PluginRunModeStateMapping mapping = stateMapping(snapshot);
            StatusEnum queryStatus = queryBaseline.getStatus();
            if (!queryStatus.isFinalState()) {
                listener.terminalSince = 0L;
                if (mapping == null) {
                    log.warn("未匹配到插件运行模式状态映射, taskInstanceId={}, pluginType={}, runMode={}",
                            taskInstanceId, snapshot == null ? null : snapshot.getPluginType(),
                            snapshot == null ? null : snapshot.getRunMode());
                    return;
                }
                if (isWaitingForRuntimeRef(queryBaseline)) {
                    return;
                }
            }

            // 2. 实现层查询本地进程或第三方系统，并将其状态映射为 Worker 的 StatusEnum。
            StatusEnum mappedStatus = queryStatus.isFinalState()
                    ? queryStatus : mapping.mapState(snapshot, queryBaseline);

            // 3. 第三方查询完成后无锁读取最新状态 S0，作为进入任务锁前的 CAS 基线。
            WorkerTaskExecutionState latestState = stateStore.readState(taskInstanceId).orElse(null);
            if (latestState == null || latestState.getStatus() == null) {
                log.warn("任务运行态在查询期间被删除, 注销状态监听, taskInstanceId={}", taskInstanceId);
                unregister(taskInstanceId);
                return;
            }

            // 4. Q.revision != S0.revision 表示查询期间发生了 submit、stop 或 kill，直接丢弃结果，不获取任务锁。
            if (queryBaseline.getRevision() != latestState.getRevision()) {
                log.info("任务运行态在查询期间已变化, 丢弃本次刷新结果, taskInstanceId={}, queryStatus={},"
                                + " latestStatus={}, queryRevision={}, latestRevision={}",
                        taskInstanceId, queryStatus, latestState.getStatus(), queryBaseline.getRevision(),
                        latestState.getRevision());
                return;
            }

            StatusEnum latestStatus = latestState.getStatus();
            // 5. mappedStatus == S0.status 时不写状态；稳定非终态也无需补偿上报，因此不获取任务锁。
            if (mappedStatus == latestStatus && listener.observedStatus == latestStatus
                    && !listener.reportPending && !latestStatus.isFinalState()) {
                queryFailureCounts.remove(taskInstanceId);
                return;
            }
            commitAndReport(listener, snapshot, mapping, latestStatus, latestState.getRevision(), mappedStatus);
        } catch (Exception e) {
            log.warn("刷新任务状态失败, taskInstanceId={}", taskInstanceId, e);
        }
    }

    int listenerCount() {
        return listeners.size();
    }

    boolean isRegistered(String taskInstanceId) {
        return listeners.containsKey(taskInstanceId);
    }

    /**
     * 幂等注册任务监听.
     *
     * @param taskInstanceId 任务实例 ID
     * @param observedStatus 注册时已观察到的本地状态
     * @param reportPending  是否需要在首次刷新时补偿上报
     */
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

    /**
     * 提交映射状态并在锁外上报 Manager.
     *
     * <p>第一次任务锁用于校验查询基线、写入状态并生成不可变的上报快照。Manager 调用完成后再次短暂加锁，
     * 仅当上报快照的 {@code status + revision} 仍是最新值时清除待上报标记。
     *
     * @param listener         任务监听项
     * @param snapshot         任务提交快照
     * @param mapping          插件状态映射器
     * @param latestStatus     锁外最新状态 S0
     * @param latestRevision   锁外最新运行态版本 S0.revision
     * @param mappedStatus     第三方状态映射结果
     */
    private void commitAndReport(TaskStateListener listener, WorkerTaskExecutionSnap snapshot,
            PluginRunModeStateMapping mapping, StatusEnum latestStatus, long latestRevision,
            StatusEnum mappedStatus) {
        // 6. mappedStatus != S0.status，或存在补偿上报时，获取任务锁并再次读取状态 S1。
        ReportAttempt reportAttempt = stateStore.withTaskLock(listener.taskInstanceId, () -> {
            if (listeners.get(listener.taskInstanceId) != listener) {
                return null;
            }
            WorkerTaskExecutionState state = stateStore.readState(listener.taskInstanceId).orElse(null);
            // 7. S1.revision != S0.revision 表示等待锁期间状态发生变化，丢弃本次映射结果。
            if (state == null || state.getRevision() != latestRevision) {
                log.info("任务运行态在等待锁期间已变化, 丢弃本次刷新结果, taskInstanceId={}, latestStatus={},"
                                + " lockedStatus={}, latestRevision={}, lockedRevision={}",
                        listener.taskInstanceId, latestStatus, state == null ? null : state.getStatus(),
                        latestRevision, state == null ? null : state.getRevision());
                return null;
            }

            // 8. revision 一致后才校验 canApplyMappedState；saveState 在任务锁内写入状态并将 revision 加 1。
            StatusEnum nextStatus = latestStatus.isFinalState()
                    ? latestStatus : normalizeUnknown(state, mappedStatus);
            if (nextStatus == null) {
                return null;
            }
            if (listener.observedStatus != latestStatus) {
                listener.reportPending = true;
                listener.observedStatus = latestStatus;
            }
            boolean statusChanged = nextStatus != latestStatus;
            if (statusChanged) {
                if (!canApplyMappedState(latestStatus, nextStatus)) {
                    log.warn("拒绝插件状态覆盖当前任务状态, taskInstanceId={}, currentStatus={}, mappedStatus={}",
                            listener.taskInstanceId, latestStatus, nextStatus);
                    return null;
                }
                state.setStatus(nextStatus);
            }
            boolean finalResultChanged = (listener.reportPending || statusChanged)
                    && state.getStatus().isFinalState() && mapping != null
                    && mapping.prepareFinalReport(snapshot, state);
            if (statusChanged || finalResultChanged) {
                long previousRevision = state.getRevision();
                stateStore.saveState(state);
                state = stateStore.readState(listener.taskInstanceId).orElse(null);
                if (state == null || state.getStatus() != nextStatus || state.getRevision() <= previousRevision) {
                    log.warn("任务状态写入失败, 暂不上报, taskInstanceId={}, expectedStatus={}",
                            listener.taskInstanceId, nextStatus);
                    return null;
                }
                listener.reportPending = true;
            }
            if (statusChanged) {
                listener.observedStatus = state.getStatus();
                log.info("提交任务状态变化, taskInstanceId={}, oldStatus={}, newStatus={}",
                        listener.taskInstanceId, latestStatus, nextStatus);
            }
            if (!listener.reportPending) {
                retainIfFinal(listener, state.getStatus());
                return null;
            }
            listener.terminalSince = 0L;
            return new ReportAttempt(toTaskResult(snapshot, state), state.getStatus(), state.getRevision());
        });
        if (reportAttempt == null) {
            return;
        }
        // Manager 网络调用不持有任务锁，避免阻塞同一任务的 stop、kill 和 submit 状态写入。
        boolean reported;
        try {
            reported = resultReporter.report(reportAttempt.result);
        } catch (RuntimeException e) {
            log.warn("任务状态上报异常, 等待下次监听重试, taskInstanceId={}, status={}",
                    listener.taskInstanceId, reportAttempt.status, e);
            return;
        }
        if (!reported) {
            log.warn("任务状态上报失败, 等待下次监听重试, taskInstanceId={}, status={}",
                    listener.taskInstanceId, reportAttempt.status);
            return;
        }
        // 只确认本次确实上报的版本；上报期间出现的新版本继续保留 reportPending。
        stateStore.withTaskLock(listener.taskInstanceId, () -> {
            if (listeners.get(listener.taskInstanceId) != listener) {
                return null;
            }
            WorkerTaskExecutionState state = stateStore.readState(listener.taskInstanceId).orElse(null);
            if (state == null || state.getStatus() != reportAttempt.status
                    || state.getRevision() != reportAttempt.revision) {
                log.info("任务状态在上报期间已变化, 保留待上报标记, taskInstanceId={}, reportedStatus={},"
                                + " latestStatus={}, reportedRevision={}, latestRevision={}",
                        listener.taskInstanceId, reportAttempt.status, state == null ? null : state.getStatus(),
                        reportAttempt.revision, state == null ? null : state.getRevision());
                return null;
            }
            listener.reportPending = false;
            retainIfFinal(listener, state.getStatus());
            log.info("任务状态上报成功, taskInstanceId={}, status={}", listener.taskInstanceId, state.getStatus());
            return null;
        });
    }

    /**
     * 将已完成上报的终态监听切换为一次性延迟清理任务.
     *
     * <p>{@link #listeners} 是唯一监听注册表，{@code terminalSince > 0} 表示监听已进入终态保留期。
     * 只有终态进入事件会按该时间排序并检查容量，不执行周期性全表扫描。
     *
     * @param listener 任务监听项
     * @param status   当前任务状态
     */
    private void retainIfFinal(TaskStateListener listener, StatusEnum status) {
        if (status != null && status.isFinalState()) {
            List<String> overflowTaskIds;
            synchronized (listeners) {
                if (listener.terminalSince > 0L) {
                    return;
                }
                listener.terminalSince = System.currentTimeMillis();
                queryFailureCounts.remove(listener.taskInstanceId);
                ScheduledFuture<?> retentionFuture = scheduler.schedule(
                        () -> evictTerminalListener(listener.taskInstanceId), listenerRetentionMs,
                        TimeUnit.MILLISECONDS);
                listener.future.cancel(false);
                listener.future = retentionFuture;

                List<TaskStateListener> terminalListeners = listeners.values()
                        .stream()
                        .filter(item -> item.terminalSince > 0L && !item.reportPending)
                        .sorted(Comparator.comparingLong((TaskStateListener item) -> item.terminalSince)
                                .thenComparing(item -> item.taskInstanceId))
                        .toList();
                int overflow = Math.max(terminalListeners.size() - listenerRetentionNum, 0);
                overflowTaskIds = terminalListeners.stream()
                        .limit(overflow)
                        .map(item -> item.taskInstanceId)
                        .toList();
            }
            overflowTaskIds.forEach(taskInstanceId -> scheduler.execute(
                    () -> evictTerminalListener(taskInstanceId)));
            return;
        }
        listener.terminalSince = 0L;
    }

    /**
     * 从唯一监听注册表原子淘汰一个已完成上报的终态监听.
     *
     * <p>终态监听不再参与状态写入，因此这里只使用 {@link ConcurrentHashMap#computeIfPresent(Object,
     * java.util.function.BiFunction)} 与终态容量临界区协调内存删除，不获取任务状态锁。
     *
     * @param taskInstanceId 任务实例 ID
     */
    private void evictTerminalListener(String taskInstanceId) {
        synchronized (listeners) {
            listeners.computeIfPresent(taskInstanceId, (key, listener) -> {
                if (listener.reportPending || listener.terminalSince == 0L) {
                    return listener;
                }
                listener.future.cancel(false);
                queryFailureCounts.remove(taskInstanceId);
                log.info("清理终态任务监听, taskInstanceId={}, status={}",
                        taskInstanceId, listener.observedStatus);
                return null;
            });
        }
    }

    /**
     * 按连续失败阈值归一 UNKNOWN，避免一次查询抖动直接推进终态.
     *
     * @param state        当前本地运行态
     * @param mappedStatus 第三方状态映射结果
     * @return 本次允许参与状态迁移的状态
     */
    private StatusEnum normalizeUnknown(WorkerTaskExecutionState state, StatusEnum mappedStatus) {
        if (mappedStatus != StatusEnum.UNKNOWN) {
            queryFailureCounts.remove(state.getTaskInstanceId());
            return mappedStatus;
        }
        int failureCount = queryFailureCounts.merge(state.getTaskInstanceId(), 1, Integer::sum);
        return failureCount < unknownThreshold ? state.getStatus() : StatusEnum.UNKNOWN;
    }

    /**
     * 校验第三方映射状态是否可以覆盖当前控制意图.
     *
     * @param currentStatus 当前本地状态
     * @param mappedStatus  第三方映射状态
     * @return 是否允许提交映射状态
     */
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
        return pluginType.trim().toUpperCase(Locale.ROOT) + ':' + runMode.trim().toUpperCase(Locale.ROOT);
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

    /**
     * 单次状态上报快照.
     */
    private static final class ReportAttempt {

        /**
         * 上报结果.
         */
        private final TaskResult result;

        /**
         * 上报状态.
         */
        private final StatusEnum status;

        /**
         * 上报运行态版本.
         */
        private final long revision;

        private ReportAttempt(TaskResult result, StatusEnum status, long revision) {
            this.result = result;
            this.status = status;
            this.revision = revision;
        }
    }
}
