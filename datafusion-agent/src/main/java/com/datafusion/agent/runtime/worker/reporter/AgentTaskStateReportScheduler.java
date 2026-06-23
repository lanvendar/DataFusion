package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 任务状态刷新与上报计划.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Slf4j
public class AgentTaskStateReportScheduler {

    /**
     * 默认运行模式.
     */
    private static final String DEFAULT_RUN_MODE = "LOCAL";

    /**
     * 任务执行状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 任务结果上报器.
     */
    private final TaskResultReporter resultReporter;

    /**
     * 状态刷新调度器.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 插件运行模式状态映射.
     */
    private final Map<String, PluginRunModeStateMapping> stateMappingMap;

    /**
     * 查询失败计数.
     */
    private final Map<String, Integer> queryFailureCountMap = new ConcurrentHashMap<>();

    /**
     * 刷新间隔.
     */
    private final long refreshIntervalMs;

    /**
     * UNKNOWN 推进阈值.
     */
    private final int unknownThreshold;

    /**
     * 构造函数.
     *
     * @param stateStore        任务执行状态存储
     * @param resultReporter    任务结果上报器
     * @param scheduler         状态刷新调度器
     * @param stateMappings     插件运行模式状态映射
     * @param refreshIntervalMs 刷新间隔
     * @param unknownThreshold  UNKNOWN 推进阈值
     */
    public AgentTaskStateReportScheduler(WorkerTaskExecutionStore stateStore, TaskResultReporter resultReporter,
            ScheduledExecutorService scheduler, List<PluginRunModeStateMapping> stateMappings, long refreshIntervalMs,
            int unknownThreshold) {
        this.stateStore = stateStore;
        this.resultReporter = resultReporter;
        this.scheduler = scheduler;
        this.refreshIntervalMs = refreshIntervalMs;
        this.unknownThreshold = unknownThreshold;
        List<PluginRunModeStateMapping> mappings = stateMappings == null ? Collections.emptyList() : stateMappings;
        this.stateMappingMap = mappings.stream()
                .filter(Objects::nonNull)
                .filter(mapping -> !isBlank(mapping.pluginType()) && !isBlank(mapping.runMode()))
                .collect(Collectors.toMap(mapping -> mappingKey(mapping.pluginType(), mapping.runMode()),
                        Function.identity(), (left, right) -> left));
    }

    /**
     * 启动周期状态刷新.
     */
    public void start() {
        long interval = Math.max(refreshIntervalMs, 1000L);
        scheduler.scheduleWithFixedDelay(this::refreshStates, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void refreshStates() {
        for (WorkerTaskExecutionState state : stateStore.listListeningStates()) {
            try {
                refreshState(state);
            } catch (Exception e) {
                log.warn("刷新任务状态失败, taskInstanceId={}", state.getTaskInstanceId(), e);
            }
        }
    }

    private void refreshState(WorkerTaskExecutionState state) {
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(state.getTaskInstanceId()).orElse(null);
        if (state.getStatus() != null && state.getStatus().isFinalState()) {
            reportFinalState(snapshot, state);
            return;
        }
        PluginRunModeStateMapping mapping = stateMapping(snapshot);
        if (mapping == null) {
            log.warn("未匹配到插件运行模式状态映射, taskInstanceId={}, pluginType={}, runMode={}",
                    state.getTaskInstanceId(), snapshot == null ? null : snapshot.getPluginType(),
                    snapshot == null ? null : snapshot.getRunMode());
            return;
        }
        StatusEnum mappedStatus = mapping.mapState(state);
        StatusEnum nextStatus = normalizeUnknown(state, mappedStatus);
        if (nextStatus == null) {
            return;
        }
        if (nextStatus != state.getStatus()) {
            state.setStatus(nextStatus);
            stateStore.saveState(state);
        }
        if (nextStatus.isFinalState()) {
            reportFinalState(snapshot, state);
        } else {
            resultReporter.report(toTaskResult(snapshot, state));
        }
    }

    private void reportFinalState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        String taskInstanceId = state.getTaskInstanceId();
        if (resultReporter.report(toTaskResult(snapshot, state))) {
            stateStore.remove(taskInstanceId);
        }
        queryFailureCountMap.remove(taskInstanceId);
    }

    private PluginRunModeStateMapping stateMapping(WorkerTaskExecutionSnap snapshot) {
        if (snapshot == null) {
            return null;
        }
        return stateMappingMap.get(mappingKey(snapshot.getPluginType(), resolveRunMode(snapshot.getRunMode())));
    }

    private String resolveRunMode(String runMode) {
        if (isBlank(runMode)) {
            return DEFAULT_RUN_MODE;
        }
        return runMode;
    }

    private StatusEnum normalizeUnknown(WorkerTaskExecutionState state, StatusEnum mappedStatus) {
        if (mappedStatus != StatusEnum.UNKNOWN) {
            queryFailureCountMap.remove(state.getTaskInstanceId());
            return mappedStatus;
        }
        int failureCount = queryFailureCountMap.merge(state.getTaskInstanceId(), 1, Integer::sum);
        if (failureCount < Math.max(unknownThreshold, 1)) {
            return state.getStatus();
        }
        return StatusEnum.UNKNOWN;
    }

    private TaskResult toTaskResult(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return TaskResult.builder()
                .taskInstanceId(state.getTaskInstanceId())
                .flowInstanceId(snapshot == null ? null : snapshot.getFlowInstanceId())
                .taskName(snapshot == null ? null : snapshot.getTaskName())
                .workerId(state.getWorkerId() == null && snapshot != null ? snapshot.getWorkerId() : state.getWorkerId())
                .taskState(state.getStatus())
                .appId(state.getAppId())
                .workDirPath(state.getWorkDirPath())
                .submitMode(SubmitModeEnum.ASYNC)
                .result(state.getResult())
                .build();
    }

    private static String mappingKey(String pluginType, String runMode) {
        return normalize(pluginType) + ':' + normalize(runMode);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
