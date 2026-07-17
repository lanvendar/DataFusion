package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.context.WorkerTaskResultMapper;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.plugin.WorkerPluginRouter;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.reporter.TaskStateListenerRegistry;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent 任务状态监听注册器.
 *
 * <p>每个任务拥有独立的周期监听。第三方查询在锁外执行，状态变化统一交给
 * {@link WorkerTaskStateCoordinator} 提交，Manager 上报也不占用任务状态锁。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
public final class AgentTaskStateListenerRegistry implements TaskStateListenerRegistry {

    /** 任务执行存储. */
    private final WorkerTaskExecutionStore stateStore;

    /** 状态协调器. */
    private final WorkerTaskStateCoordinator stateCoordinator;

    /** 插件路由器. */
    private final WorkerPluginRouter pluginRouter;

    /** 单次结果上报器. */
    private final TaskResultReporter resultReporter;

    /** 任务监听调度器. */
    private final ScheduledExecutorService scheduler;

    /** 活跃和终态保留期内的监听项. */
    private final Map<String, TaskStateListener> listeners = new ConcurrentHashMap<>();

    /** 与查询基线绑定的连续 UNKNOWN 计数. */
    private final Map<String, QueryFailure> queryFailures = new ConcurrentHashMap<>();

    /** 刷新间隔. */
    private final long refreshIntervalMs;

    /** UNKNOWN 推进阈值. */
    private final int unknownThreshold;

    /** 终态监听保留时间. */
    private final long listenerRetentionMs;

    /** 终态监听最大保留数量. */
    private final int listenerRetentionNum;

    /**
     * 创建 Agent 任务状态监听注册器.
     *
     * @param stateStore           任务执行存储
     * @param stateCoordinator     状态协调器
     * @param pluginRouter         插件路由器
     * @param resultReporter       单次结果上报器
     * @param scheduler            任务监听调度器
     * @param refreshIntervalMs    刷新间隔
     * @param unknownThreshold     UNKNOWN 推进阈值
     * @param listenerRetentionMs  终态监听保留时间
     * @param listenerRetentionNum 终态监听最大保留数量
     */
    public AgentTaskStateListenerRegistry(WorkerTaskExecutionStore stateStore,
            WorkerTaskStateCoordinator stateCoordinator, WorkerPluginRouter pluginRouter,
            TaskResultReporter resultReporter, ScheduledExecutorService scheduler,
            long refreshIntervalMs, int unknownThreshold, long listenerRetentionMs, int listenerRetentionNum) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore不能为空");
        this.stateCoordinator = Objects.requireNonNull(stateCoordinator, "stateCoordinator不能为空");
        this.pluginRouter = Objects.requireNonNull(pluginRouter, "pluginRouter不能为空");
        this.resultReporter = Objects.requireNonNull(resultReporter, "resultReporter不能为空");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler不能为空");
        this.refreshIntervalMs = Math.max(refreshIntervalMs, 1000L);
        this.unknownThreshold = Math.max(unknownThreshold, 1);
        this.listenerRetentionMs = Math.max(listenerRetentionMs, 0L);
        this.listenerRetentionNum = Math.max(listenerRetentionNum, 0);
    }

    /**
     * 幂等注册单任务周期监听.
     *
     * @param taskInstanceId 任务实例 ID
     * @param reportedStatus Manager 已通过当前 RPC 获知的状态；恢复注册时为 {@code null}
     */
    @Override
    public void register(String taskInstanceId, StatusEnum reportedStatus) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        listeners.compute(taskInstanceId, (key, current) -> {
            if (current != null) {
                current.reportedStatus = reportedStatus;
                if (current.terminalSince > 0L) {
                    current.future.cancel(false);
                    current.terminalSince = 0L;
                    current.future = schedule(taskInstanceId);
                }
                return current;
            }
            TaskStateListener listener = new TaskStateListener(taskInstanceId, reportedStatus);
            listener.future = schedule(taskInstanceId);
            log.info("注册任务状态监听, taskInstanceId={}, intervalMs={}, reportedStatus={}",
                    taskInstanceId, refreshIntervalMs, reportedStatus);
            return listener;
        });
    }

    /**
     * 注销单任务监听.
     *
     * @param taskInstanceId 任务实例 ID
     */
    @Override
    public void unregister(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        TaskStateListener listener = listeners.remove(taskInstanceId);
        if (listener != null) {
            listener.future.cancel(false);
            queryFailures.remove(taskInstanceId);
            log.info("注销任务状态监听, taskInstanceId={}", taskInstanceId);
        }
    }

    /**
     * 委托底层传输实现上报一次任务结果.
     *
     * @param result 任务结果
     * @return 是否上报成功
     */
    @Override
    public boolean report(TaskResult result) {
        return resultReporter.report(result);
    }

    /**
     * 取消并清空全部任务监听.
     */
    @Override
    public void shutdown() {
        listeners.values().forEach(listener -> listener.future.cancel(false));
        listeners.clear();
        queryFailures.clear();
    }

    /**
     * 执行单个任务的一次状态刷新.
     *
     * <p>固定流程如下：
     *
     * <ol>
     *   <li>无锁读取查询基线 Q：status 和 revision。</li>
     *   <li>映射器查询第三方并转换为 {@link StatusEnum}。</li>
     *   <li>Coordinator 再读取最新状态 S0。</li>
     *   <li>Q.revision 与 S0.revision 不一致时丢弃本次查询。</li>
     *   <li>映射状态与 S0.status 一致时不写入。</li>
     *   <li>状态不一致时进入 Store 的任务级 CAS。</li>
     *   <li>等待锁期间 revision 变化时丢弃结果。</li>
     *   <li>revision 一致才校验迁移、写入状态并将 revision 加一。</li>
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
            WorkerTaskExecutionState baseline = stateStore.readState(taskInstanceId).orElse(null);
            WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(taskInstanceId).orElse(null);
            if (baseline == null || baseline.getStatus() == null || snapshot == null) {
                log.warn("任务执行记录不完整, 注销状态监听, taskInstanceId={}", taskInstanceId);
                unregister(taskInstanceId);
                return;
            }

            WorkerTaskExecutionState actual = baseline;
            PluginRunModeStateMapping mapping = pluginRouter.routeStateMapping(
                    snapshot.getPluginType(), snapshot.getRunMode());
            if (!baseline.getStatus().isFinalState()) {
                if (mapping == null) {
                    log.warn("未匹配到插件状态映射, taskInstanceId={}, pluginType={}, runMode={}",
                            taskInstanceId, snapshot.getPluginType(), snapshot.getRunMode());
                    return;
                }
                StatusEnum mappedStatus = normalizeUnknown(baseline, mapping.mapState(snapshot, baseline));
                if (mappedStatus == null) {
                    return;
                }
                if (mappedStatus != baseline.getStatus()) {
                    WorkerTaskExecutionState candidate = baseline.copy();
                    candidate.setStatus(mappedStatus);
                    if (mappedStatus.isFinalState()) {
                        mapping.prepareFinalReport(snapshot, candidate);
                    }
                    actual = stateCoordinator.commitMappedState(baseline, candidate);
                }
            } else if (listener.reportedStatus != baseline.getStatus() && mapping != null) {
                WorkerTaskExecutionState candidate = baseline.copy();
                if (mapping.prepareFinalReport(snapshot, candidate)) {
                    actual = stateCoordinator.commitFinalReport(baseline, candidate);
                }
            }

            if (listener.reportedStatus == actual.getStatus()) {
                retainIfFinal(listener, actual.getStatus());
                return;
            }
            TaskResult reportResult = WorkerTaskResultMapper.toTaskResult(snapshot, actual);
            if (!resultReporter.report(reportResult)) {
                log.warn("任务状态上报失败, 等待下次监听重试, taskInstanceId={}, status={}",
                        taskInstanceId, reportResult.getTaskState());
                return;
            }
            if (listeners.get(taskInstanceId) != listener) {
                return;
            }
            listener.reportedStatus = reportResult.getTaskState();
            log.info("任务状态上报成功, taskInstanceId={}, status={}",
                    taskInstanceId, reportResult.getTaskState());
            retainIfFinal(listener, reportResult.getTaskState());
        } catch (Exception e) {
            log.warn("刷新任务状态失败, taskInstanceId={}", taskInstanceId, e);
        }
    }

    /**
     * 获取当前监听数量.
     *
     * @return 监听数量
     */
    int listenerCount() {
        return listeners.size();
    }

    /**
     * 判断任务是否已注册监听.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 是否已注册
     */
    boolean isRegistered(String taskInstanceId) {
        return listeners.containsKey(taskInstanceId);
    }

    private ScheduledFuture<?> schedule(String taskInstanceId) {
        return scheduler.scheduleWithFixedDelay(() -> refreshTask(taskInstanceId),
                refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
    }

    private StatusEnum normalizeUnknown(WorkerTaskExecutionState baseline, StatusEnum mappedStatus) {
        if (mappedStatus != StatusEnum.UNKNOWN) {
            queryFailures.remove(baseline.getTaskInstanceId());
            return mappedStatus;
        }
        QueryFailure failure = queryFailures.compute(baseline.getTaskInstanceId(), (key, current) -> {
            boolean sameBaseline = current != null && current.revision == baseline.getRevision()
                    && current.status == baseline.getStatus() && Objects.equals(current.appId, baseline.getAppId());
            return new QueryFailure(baseline.getRevision(), baseline.getStatus(), baseline.getAppId(),
                    sameBaseline ? current.count + 1 : 1);
        });
        return failure.count < unknownThreshold ? baseline.getStatus() : StatusEnum.UNKNOWN;
    }

    private void retainIfFinal(TaskStateListener listener, StatusEnum status) {
        if (status == null || !status.isFinalState() || listener.terminalSince > 0L) {
            return;
        }
        List<String> overflowTaskIds;
        synchronized (listeners) {
            if (listener.terminalSince > 0L || listeners.get(listener.taskInstanceId) != listener) {
                return;
            }
            listener.terminalSince = System.currentTimeMillis();
            listener.future.cancel(false);
            listener.future = scheduler.schedule(() -> evictTerminalListener(listener.taskInstanceId),
                    listenerRetentionMs, TimeUnit.MILLISECONDS);
            queryFailures.remove(listener.taskInstanceId);
            List<TaskStateListener> terminalListeners = listeners.values().stream()
                    .filter(item -> item.terminalSince > 0L)
                    .sorted(Comparator.comparingLong((TaskStateListener item) -> item.terminalSince)
                            .thenComparing(item -> item.taskInstanceId))
                    .toList();
            int overflow = Math.max(terminalListeners.size() - listenerRetentionNum, 0);
            overflowTaskIds = terminalListeners.stream().limit(overflow)
                    .map(item -> item.taskInstanceId).toList();
        }
        overflowTaskIds.forEach(taskInstanceId -> scheduler.execute(() -> evictTerminalListener(taskInstanceId)));
    }

    private void evictTerminalListener(String taskInstanceId) {
        synchronized (listeners) {
            listeners.computeIfPresent(taskInstanceId, (key, listener) -> {
                if (listener.terminalSince == 0L) {
                    return listener;
                }
                listener.future.cancel(false);
                queryFailures.remove(taskInstanceId);
                log.info("清理终态任务监听, taskInstanceId={}, status={}",
                        taskInstanceId, listener.reportedStatus);
                return null;
            });
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 单任务监听注册项. */
    private static final class TaskStateListener {

        /** 任务实例 ID. */
        private final String taskInstanceId;

        /** 当前计划句柄. */
        private ScheduledFuture<?> future;

        /** Manager 已成功接收的最近状态. */
        private volatile StatusEnum reportedStatus;

        /** 进入终态保留期的时间. */
        private volatile long terminalSince;

        private TaskStateListener(String taskInstanceId, StatusEnum reportedStatus) {
            this.taskInstanceId = taskInstanceId;
            this.reportedStatus = reportedStatus;
        }
    }

    /**
     * 与一次查询基线绑定的 UNKNOWN 计数.
     *
     * @param revision 查询基线 revision
     * @param status   查询基线状态
     * @param appId    查询基线运行引用
     * @param count    连续 UNKNOWN 次数
     */
    private record QueryFailure(long revision, StatusEnum status, String appId, int count) {
    }
}
