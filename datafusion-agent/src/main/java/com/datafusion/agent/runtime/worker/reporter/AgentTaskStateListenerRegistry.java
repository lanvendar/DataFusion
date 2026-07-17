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
 * <p>每个任务只注册一个周期监听。刷新过程只读取一次查询基线，在锁外查询并映射第三方状态；状态变化时由存储层
 * 使用 revision 完成 CAS 写入。Manager 上报不持有任务锁。
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
     * <p>该方法只维护线程安全的内存监听表，不参与 {@code .state} 复合写入。任务控制入口应先完成状态提交，
     * 启动恢复入口应在 Agent 对外就绪前调用。
     *
     * @param taskInstanceId 任务实例 ID
     * @param reportedStatus 当前任务控制响应返回给 Manager 的状态
     */
    public void register(String taskInstanceId, StatusEnum reportedStatus) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        registerTask(taskInstanceId, reportedStatus);
    }

    /**
     * 注销任务状态监听.
     *
     * <p>监听删除和定时任务取消不修改任务运行态，因此不获取任务状态锁。
     *
     * @param taskInstanceId 任务实例 ID
     */
    public void unregister(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        TaskStateListener listener = listeners.remove(taskInstanceId);
        if (listener != null) {
            listener.future.cancel(false);
            queryFailureCounts.remove(taskInstanceId);
            log.info("注销任务状态监听, taskInstanceId={}", taskInstanceId);
        }
    }

    /**
     * 恢复 Manager 返回的未完成任务.
     *
     * <p>恢复由 Agent 未就绪阶段串行执行，此时任务控制接口尚未开放，因此只需读取已恢复的本地状态并注册监听。
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
            WorkerTaskExecutionState state = stateStore.readState(task.getTaskInstanceId()).orElse(null);
            if (state == null || state.getStatus() == null) {
                continue;
            }
            registerTask(task.getTaskInstanceId(), null);
            restoredCount++;
        }
        log.info("恢复agent任务状态监听, taskCount={}", restoredCount);
    }

    /**
     * 执行单个任务的一次状态刷新.
     *
     * <p>关键流程如下：
     *
     * <ol>
     *   <li>无锁读取查询基线 Q，包含 status 和 revision。</li>
     *   <li>实现层查询本地进程或第三方系统，并映射为 {@link StatusEnum}。</li>
     *   <li>将映射状态与 Q.status 比较。</li>
     *   <li>状态一致时不写入；已上报的非终态直接结束。</li>
     *   <li>状态不一致时校验 {@code canApplyMappedState}。</li>
     *   <li>通过 {@code saveState(state, Q.revision)} 尝试 CAS 写入。</li>
     *   <li>存储层在任务锁内复读 revision；不一致返回 {@code false}并丢弃映射结果。</li>
     *   <li>revision 一致时写入状态并自增 1，然后在锁外上报 Manager。</li>
     * </ol>
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
            StatusEnum mappedStatus = queryStatus.isFinalState() ? queryStatus : mapping.mapState(snapshot, queryBaseline);

            // 3. 直接与 Q.status 比较；稳定且已上报的非终态不写入。
            if (mappedStatus == queryStatus && listener.reportedStatus == queryStatus && !queryStatus.isFinalState()) {
                queryFailureCounts.remove(taskInstanceId);
                return;
            }
            // 4. 状态提交，保存和上报
            commitAndReport(listener, snapshot, mapping, queryBaseline, mappedStatus);
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
     * @param reportedStatus Manager 已通过当前任务控制响应接收的状态；恢复注册时为 null
     */
    private void registerTask(String taskInstanceId, StatusEnum reportedStatus) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        listeners.compute(taskInstanceId, (key, current) -> {
            if (current != null) {
                current.reportedStatus = reportedStatus;
                if (current.terminalSince > 0L) {
                    current.future.cancel(false);
                    current.terminalSince = 0L;
                    current.future = scheduler.scheduleWithFixedDelay(() -> refreshTask(taskInstanceId),
                            refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
                    log.info("重新激活任务状态监听, taskInstanceId={}, intervalMs={}, reportedStatus={}",
                            taskInstanceId, refreshIntervalMs, reportedStatus);
                }
                return current;
            }
            TaskStateListener listener = new TaskStateListener(taskInstanceId, reportedStatus);
            listener.future = scheduler.scheduleWithFixedDelay(() -> refreshTask(taskInstanceId), refreshIntervalMs,
                    refreshIntervalMs, TimeUnit.MILLISECONDS);
            log.info("注册任务状态监听, taskInstanceId={}, intervalMs={}, reportedStatus={}",
                    taskInstanceId, refreshIntervalMs, reportedStatus);
            return listener;
        });
    }

    /**
     * 使用 revision CAS 提交映射状态，并上报 Manager.
     *
     * <p>方法内不获取任务锁。状态写入的锁与 revision 校验由 {@link WorkerTaskExecutionStore#saveState(
     * WorkerTaskExecutionState, long)} 内部完成。Manager 上报成功后才推进 {@code reportedStatus}，失败则在下一周期重试。
     *
     * @param listener         任务监听项
     * @param snapshot         任务提交快照
     * @param mapping          插件状态映射器
     * @param state            查询基线 Q
     * @param mappedStatus     第三方状态映射结果
     */
    private void commitAndReport(TaskStateListener listener, WorkerTaskExecutionSnap snapshot,
            PluginRunModeStateMapping mapping, WorkerTaskExecutionState state, StatusEnum mappedStatus) {
        if (listeners.get(listener.taskInstanceId) != listener) {
            return;
        }
        StatusEnum currentStatus = state.getStatus();
        StatusEnum nextStatus = currentStatus.isFinalState() ? currentStatus : normalizeUnknown(state, mappedStatus);
        if (nextStatus == null) {
            return;
        }
        boolean statusChanged = nextStatus != currentStatus;
        if (statusChanged && !canApplyMappedState(currentStatus, nextStatus)) {
            log.warn("拒绝插件状态覆盖当前任务状态, taskInstanceId={}, currentStatus={}, mappedStatus={}",
                    listener.taskInstanceId, currentStatus, nextStatus);
            return;
        }
        if (statusChanged) {
            state.setStatus(nextStatus);
        }
        boolean shouldReport = listener.reportedStatus != nextStatus;
        boolean finalResultChanged = shouldReport && nextStatus.isFinalState() && mapping != null
                && mapping.prepareFinalReport(snapshot, state);
        if (statusChanged || finalResultChanged) {
            if (!stateStore.saveState(state, state.getRevision())) {
                if (mappedStatus == StatusEnum.UNKNOWN) {
                    queryFailureCounts.remove(listener.taskInstanceId);
                }
                return;
            }
        }
        if (statusChanged) {
            log.info("提交任务状态变化, taskInstanceId={}, oldStatus={}, newStatus={}",
                    listener.taskInstanceId, currentStatus, nextStatus);
        }
        if (!shouldReport) {
            retainIfFinal(listener, nextStatus);
            return;
        }
        listener.terminalSince = 0L;
        TaskResult reportResult = toTaskResult(snapshot, state);
        if (reportResult == null) {
            return;
        }
        boolean reported;
        try {
            reported = resultReporter.report(reportResult);
        } catch (RuntimeException e) {
            log.warn("任务状态上报异常, 等待下次监听重试, taskInstanceId={}, status={}",
                    listener.taskInstanceId, reportResult.getTaskState(), e);
            return;
        }
        if (!reported) {
            log.warn("任务状态上报失败, 等待下次监听重试, taskInstanceId={}, status={}",
                    listener.taskInstanceId, reportResult.getTaskState());
            return;
        }
        if (listeners.get(listener.taskInstanceId) != listener) {
            return;
        }
        StatusEnum reportedStatus = reportResult.getTaskState();
        listener.reportedStatus = reportedStatus;
        log.info("任务状态上报成功, taskInstanceId={}, status={}", listener.taskInstanceId, reportedStatus);
        retainIfFinal(listener, reportedStatus);
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
                        .filter(item -> item.terminalSince > 0L)
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
                if (listener.terminalSince == 0L) {
                    return listener;
                }
                listener.future.cancel(false);
                queryFailureCounts.remove(taskInstanceId);
                log.info("清理终态任务监听, taskInstanceId={}, status={}",
                        taskInstanceId, listener.reportedStatus);
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
         * 最近一次已成功上报给 Manager 的状态.
         */
        private volatile StatusEnum reportedStatus;

        /**
         * 首次确认终态时间.
         */
        private volatile long terminalSince;

        private TaskStateListener(String taskInstanceId, StatusEnum reportedStatus) {
            this.taskInstanceId = taskInstanceId;
            this.reportedStatus = reportedStatus;
        }
    }
}
