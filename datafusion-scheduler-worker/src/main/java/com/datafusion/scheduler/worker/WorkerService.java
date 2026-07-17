package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.client.WorkerClient;
import com.datafusion.scheduler.worker.client.WorkerIdentityStore;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.context.WorkerTaskResultMapper;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerPluginRouter;
import com.datafusion.scheduler.worker.reporter.TaskStateListenerRegistry;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Worker 子系统统一入口.
 *
 * <p>本类管理 Worker 注册、恢复和心跳，并编排 submit、stop、kill、finish。插件只执行第三方动作，
 * 状态迁移统一交给 {@link WorkerTaskStateCoordinator}，最终响应始终从执行存储中的权威状态构造。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
public class WorkerService {

    /** Worker 与 Manager 通信客户端. */
    private final WorkerClient workerClient;

    /** Worker 本地身份存储. */
    private final WorkerIdentityStore identityStore;

    /** 插件执行器和状态映射路由. */
    private final WorkerPluginRouter pluginRouter;

    /** 任务执行存储. */
    private final WorkerTaskExecutionStore executionStore;

    /** 任务状态协调器. */
    private final WorkerTaskStateCoordinator stateCoordinator;

    /** 任务监听和结果上报入口. */
    private final TaskStateListenerRegistry listenerRegistry;

    /** 异步任务动作执行器. */
    private final Executor asyncExecutor;

    /** Worker 心跳调度器. */
    private final ScheduledExecutorService heartbeatScheduler;

    /** 默认提交模式. */
    private final SubmitModeEnum defaultSubmitMode;

    /** 心跳间隔. */
    private final long heartbeatIntervalMs;

    /** 注册完成前是否接收任务. */
    private final boolean acceptTasksBeforeRegistered;

    /** 当前 Worker. */
    private volatile Worker worker;

    /** 是否已取得 Manager 确认的 Worker 身份. */
    private volatile boolean registered;

    /** 是否已完成未结束任务恢复. */
    private volatile boolean ready;

    /** 心跳计划. */
    private volatile ScheduledFuture<?> heartbeatFuture;

    /**
     * 创建 Worker 子系统服务.
     *
     * @param workerClient                Worker 通信客户端
     * @param identityStore               Worker 本地身份存储
     * @param pluginRouter                插件路由器
     * @param executionStore              任务执行存储
     * @param stateCoordinator             任务状态协调器
     * @param listenerRegistry             任务监听注册器
     * @param asyncExecutor                异步动作执行器
     * @param heartbeatScheduler           心跳调度器
     * @param defaultSubmitMode            默认提交模式
     * @param heartbeatIntervalMs          心跳间隔
     * @param acceptTasksBeforeRegistered  注册完成前是否接收任务
     */
    public WorkerService(WorkerClient workerClient, WorkerIdentityStore identityStore,
            WorkerPluginRouter pluginRouter, WorkerTaskExecutionStore executionStore,
            WorkerTaskStateCoordinator stateCoordinator, TaskStateListenerRegistry listenerRegistry,
            Executor asyncExecutor, ScheduledExecutorService heartbeatScheduler,
            SubmitModeEnum defaultSubmitMode, long heartbeatIntervalMs, boolean acceptTasksBeforeRegistered) {
        this.workerClient = required(workerClient, "workerClient");
        this.identityStore = required(identityStore, "identityStore");
        this.pluginRouter = required(pluginRouter, "pluginRouter");
        this.executionStore = required(executionStore, "executionStore");
        this.stateCoordinator = required(stateCoordinator, "stateCoordinator");
        this.listenerRegistry = required(listenerRegistry, "listenerRegistry");
        this.asyncExecutor = required(asyncExecutor, "asyncExecutor");
        this.heartbeatScheduler = required(heartbeatScheduler, "heartbeatScheduler");
        this.defaultSubmitMode = defaultSubmitMode == null ? SubmitModeEnum.SYNC : defaultSubmitMode;
        this.heartbeatIntervalMs = Math.max(heartbeatIntervalMs, 1000L);
        this.acceptTasksBeforeRegistered = acceptTasksBeforeRegistered;
    }

    /**
     * 启动 Worker 注册、任务恢复和心跳.
     *
     * @param initialWorker Agent 解析出的 Worker 基础信息
     */
    public synchronized void start(Worker initialWorker) {
        if (worker != null) {
            return;
        }
        worker = required(initialWorker, "initialWorker");
        identityStore.load()
                .filter(local -> worker.getWorkerCode() != null
                        && worker.getWorkerCode().equals(local.getWorkerCode()) && !isBlank(local.getId()))
                .ifPresent(local -> {
                    worker.setId(local.getId());
                    worker.setRegisterTime(local.getRegisterTime());
                    registered = true;
                });
        refreshWorkerLifecycle();
        heartbeatFuture = heartbeatScheduler.scheduleWithFixedDelay(this::refreshWorkerLifecycle,
                heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止 Worker 心跳、任务监听并通知 Manager 下线.
     */
    public synchronized void stop() {
        ready = false;
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
        listenerRegistry.shutdown();
        Worker current = worker;
        if (registered && current != null && !isBlank(current.getId())) {
            Worker offline = new Worker();
            offline.setId(current.getId());
            workerClient.offline(offline);
        }
    }

    /**
     * 获取当前 Worker 是否允许接收任务.
     *
     * @return 是否允许接收任务
     */
    public boolean acceptsTasks() {
        return ready || acceptTasksBeforeRegistered;
    }

    /**
     * 获取当前 Worker 是否完成恢复.
     *
     * @return 是否已就绪
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 获取当前 Worker ID.
     *
     * @return Worker ID
     */
    public String getWorkerId() {
        Worker current = worker;
        return current == null ? null : current.getId();
    }

    /**
     * 获取当前已加载插件类型.
     *
     * @return 插件类型集合
     */
    public Set<String> pluginTypes() {
        return pluginRouter.pluginTypes();
    }

    /**
     * 提交任务.
     *
     * @param request 任务请求
     * @return 最终实际落盘的任务结果；异步提交成功入队时返回 SUBMITTING
     */
    public TaskResult submitTask(TaskRequest request) {
        validateSubmitRequest(request);
        SubmitModeEnum submitMode = request.getSubmitMode() == null ? defaultSubmitMode : request.getSubmitMode();
        request.setSubmitMode(submitMode);
        PluginTaskExecutor executor = pluginRouter.routeExecutor(request.getPluginType(), request.getRunMode());
        if (executor == null) {
            return failureResult(request, StatusEnum.SUBMIT_FAILURE,
                    "未匹配到插件执行器: " + request.getPluginType() + '/' + request.getRunMode());
        }

        WorkerTaskExecutionSnap snapshot = snapshot(request);
        String workDirPath = executionStore.saveSnapshot(snapshot);
        WorkerTaskStateCoordinator.ActionReservation reservation = stateCoordinator.reserveAction(
                snapshot, workDirPath, StatusEnum.SUBMITTING);
        if (!reservation.accepted()) {
            TaskResult rejected = result(snapshot, reservation.executionState(),
                    "当前状态不允许进入SUBMITTING");
            listenerRegistry.register(request.getTaskInstanceId(), rejected.getTaskState());
            return rejected;
        }

        RunningTaskContext context = new RunningTaskContext(snapshot, reservation.executionState(), workDirPath);
        try {
            executor.validate(context);
        } catch (RuntimeException e) {
            context.getExecutionState().setStatus(StatusEnum.SUBMIT_FAILURE);
            WorkerTaskExecutionState committed = stateCoordinator.commitActionResult(context,
                    WorkerResult.builder().message(e.getMessage()).build());
            TaskResult failed = result(snapshot, committed, null);
            listenerRegistry.register(request.getTaskInstanceId(), failed.getTaskState());
            return failed;
        }

        if (submitMode == SubmitModeEnum.ASYNC) {
            try {
                asyncExecutor.execute(() -> executeSubmit(executor, context));
            } catch (RuntimeException e) {
                context.getExecutionState().setStatus(StatusEnum.SUBMIT_FAILURE);
                WorkerTaskExecutionState committed = stateCoordinator.commitActionResult(context,
                        WorkerResult.builder().message(e.getMessage()).build());
                TaskResult failed = result(snapshot, committed, null);
                listenerRegistry.register(request.getTaskInstanceId(), failed.getTaskState());
                return failed;
            }
            TaskResult accepted = result(snapshot, reservation.executionState(), null);
            listenerRegistry.register(request.getTaskInstanceId(), StatusEnum.SUBMITTING);
            return accepted;
        }

        WorkerTaskExecutionState committed = executeSubmit(executor, context);
        TaskResult submitted = result(snapshot, committed, null);
        listenerRegistry.register(request.getTaskInstanceId(), submitted.getTaskState());
        return submitted;
    }

    /**
     * 停止任务.
     *
     * @param request 任务请求，至少包含 taskInstanceId
     * @return 最终实际落盘的任务结果
     */
    public TaskResult stopTask(TaskRequest request) {
        validateTaskInstanceId(request);
        RunningTaskContext context = controlContext(request.getTaskInstanceId(), StatusEnum.STOPPING);
        if (context == null) {
            return failureResult(request, StatusEnum.STOP_FAILURE, "任务执行记录或插件不存在");
        }
        if (context.getExecutionState().getStatus() != StatusEnum.STOPPING) {
            TaskResult rejected = result(context.getSnapshot(), context.getExecutionState(),
                    "当前状态不允许进入STOPPING");
            listenerRegistry.register(request.getTaskInstanceId(), rejected.getTaskState());
            return rejected;
        }
        PluginTaskExecutor executor = pluginRouter.routeExecutor(
                context.getSnapshot().getPluginType(), context.getSnapshot().getRunMode());
        try {
            WorkerResult workerResult = executor.stop(context);
            WorkerTaskExecutionState committed = stateCoordinator.commitActionResult(context, workerResult);
            TaskResult stopped = result(context.getSnapshot(), committed, null);
            listenerRegistry.register(request.getTaskInstanceId(), stopped.getTaskState());
            return stopped;
        } catch (RuntimeException e) {
            context.getExecutionState().setStatus(StatusEnum.STOP_FAILURE);
            WorkerTaskExecutionState committed = stateCoordinator.commitActionResult(context,
                    WorkerResult.builder().message(e.getMessage()).build());
            TaskResult failed = result(context.getSnapshot(), committed, null);
            listenerRegistry.register(request.getTaskInstanceId(), failed.getTaskState());
            return failed;
        }
    }

    /**
     * 强制停止任务.
     *
     * @param request 任务请求，至少包含 taskInstanceId
     * @return 最终实际落盘的任务结果
     */
    public TaskResult killTask(TaskRequest request) {
        validateTaskInstanceId(request);
        RunningTaskContext context = controlContext(request.getTaskInstanceId(), StatusEnum.KILLING);
        if (context == null) {
            return failureResult(request, StatusEnum.UNKNOWN, "任务执行记录或插件不存在");
        }
        if (context.getExecutionState().getStatus() != StatusEnum.KILLING) {
            TaskResult rejected = result(context.getSnapshot(), context.getExecutionState(),
                    "当前状态不允许进入KILLING");
            listenerRegistry.register(request.getTaskInstanceId(), rejected.getTaskState());
            return rejected;
        }
        PluginTaskExecutor executor = pluginRouter.routeExecutor(
                context.getSnapshot().getPluginType(), context.getSnapshot().getRunMode());
        try {
            WorkerResult workerResult = executor.kill(context);
            WorkerTaskExecutionState committed = stateCoordinator.commitActionResult(context, workerResult);
            TaskResult killed = result(context.getSnapshot(), committed, null);
            listenerRegistry.register(request.getTaskInstanceId(), killed.getTaskState());
            return killed;
        } catch (RuntimeException e) {
            context.getExecutionState().setStatus(StatusEnum.UNKNOWN);
            WorkerTaskExecutionState committed = stateCoordinator.commitActionResult(context,
                    WorkerResult.builder().message(e.getMessage()).build());
            TaskResult failed = result(context.getSnapshot(), committed, null);
            listenerRegistry.register(request.getTaskInstanceId(), failed.getTaskState());
            return failed;
        }
    }

    /**
     * 完成任务并清理本地执行记录.
     *
     * @param request 任务请求，至少包含 taskInstanceId
     * @return 插件和本地记录是否均清理完成
     */
    public boolean finishTask(TaskRequest request) {
        validateTaskInstanceId(request);
        Optional<WorkerTaskExecutionSnap> snapshot = executionStore.readSnapshot(request.getTaskInstanceId());
        Optional<WorkerTaskExecutionState> state = executionStore.readState(request.getTaskInstanceId());
        if (snapshot.isEmpty() || state.isEmpty()) {
            return false;
        }
        PluginTaskExecutor executor = pluginRouter.routeExecutor(
                snapshot.get().getPluginType(), snapshot.get().getRunMode());
        if (executor == null) {
            return false;
        }
        RunningTaskContext context = new RunningTaskContext(
                snapshot.get(), state.get().copy(), state.get().getWorkDirPath());
        try {
            if (!executor.finish(context)) {
                return false;
            }
            executionStore.deleteExecution(request.getTaskInstanceId());
            listenerRegistry.unregister(request.getTaskInstanceId());
            return true;
        } catch (RuntimeException e) {
            log.warn("清理任务执行记录失败, taskInstanceId={}", request.getTaskInstanceId(), e);
            return false;
        }
    }

    private WorkerTaskExecutionState executeSubmit(PluginTaskExecutor executor, RunningTaskContext context) {
        try {
            WorkerResult workerResult = executor.submit(context);
            if (context.getExecutionState().getStatus() == StatusEnum.SUBMITTING) {
                context.getExecutionState().setStatus(StatusEnum.SUBMIT_SUCCESS);
            }
            return stateCoordinator.commitActionResult(context, workerResult);
        } catch (RuntimeException e) {
            context.getExecutionState().setStatus(StatusEnum.SUBMIT_FAILURE);
            return stateCoordinator.commitActionResult(context,
                    WorkerResult.builder().message(e.getMessage()).build());
        }
    }

    private RunningTaskContext controlContext(String taskInstanceId, StatusEnum actionStatus) {
        WorkerTaskExecutionSnap snapshot = executionStore.readSnapshot(taskInstanceId).orElse(null);
        WorkerTaskExecutionState currentState = executionStore.readState(taskInstanceId).orElse(null);
        if (snapshot == null || currentState == null
                || pluginRouter.routeExecutor(snapshot.getPluginType(), snapshot.getRunMode()) == null) {
            return null;
        }
        WorkerTaskStateCoordinator.ActionReservation reservation = stateCoordinator.reserveAction(
                snapshot, currentState.getWorkDirPath(), actionStatus);
        WorkerTaskExecutionState executionState = reservation.executionState();
        return new RunningTaskContext(snapshot, executionState,
                executionState == null ? currentState.getWorkDirPath() : executionState.getWorkDirPath());
    }

    private synchronized void refreshWorkerLifecycle() {
        Worker current = worker;
        if (current == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            current.setLastHeartbeatTime(now);
            current.setUpdateTime(now);
            if (!registered) {
                Worker registeredWorker = workerClient.register(current);
                if (registeredWorker == null || isBlank(registeredWorker.getId())) {
                    return;
                }
                mergeWorker(current, registeredWorker);
                registered = true;
                identityStore.save(current);
            } else {
                Worker heartbeat = new Worker();
                heartbeat.setId(current.getId());
                heartbeat.setLastHeartbeatTime(now);
                Worker saved = workerClient.heartbeat(heartbeat);
                if (saved != null) {
                    mergeWorker(current, saved);
                    identityStore.save(current);
                }
            }
            if (!ready) {
                Worker identity = new Worker();
                identity.setId(current.getId());
                Optional<List<TaskRequest>> unfinishedTasks = workerClient.findUnfinishedTasks(identity);
                if (unfinishedTasks.isEmpty()) {
                    return;
                }
                Collection<String> taskInstanceIds = unfinishedTasks.get().stream()
                        .map(TaskRequest::getTaskInstanceId).filter(id -> !isBlank(id)).distinct().toList();
                executionStore.restoreExecutions(taskInstanceIds)
                        .forEach(taskInstanceId -> listenerRegistry.register(taskInstanceId, null));
                ready = true;
            }
        } catch (RuntimeException e) {
            log.warn("Worker 注册、心跳或任务恢复失败, workerId={}", current.getId(), e);
        }
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        String workerId = getWorkerId();
        if (isBlank(workerId) && request.getWorkerResult() != null) {
            workerId = request.getWorkerResult().getWorkerId();
        }
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(request.getRunMode())
                .workerId(workerId)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .submitMode(request.getSubmitMode())
                .build();
    }

    private TaskResult result(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state, String message) {
        if (state == null) {
            TaskRequest request = new TaskRequest();
            request.setFlowInstanceId(snapshot.getFlowInstanceId());
            request.setTaskInstanceId(snapshot.getTaskInstanceId());
            request.setTaskName(snapshot.getTaskName());
            request.setSubmitMode(snapshot.getSubmitMode());
            return failureResult(request, StatusEnum.UNKNOWN, message);
        }
        return WorkerTaskResultMapper.toTaskResult(snapshot, state, message);
    }

    private TaskResult failureResult(TaskRequest request, StatusEnum status, String message) {
        return TaskResult.builder()
                .flowInstanceId(request == null ? null : request.getFlowInstanceId())
                .taskInstanceId(request == null ? null : request.getTaskInstanceId())
                .taskName(request == null ? null : request.getTaskName())
                .taskState(status)
                .submitMode(request == null || request.getSubmitMode() == null
                        ? defaultSubmitMode : request.getSubmitMode())
                .workerResult(WorkerResult.builder().workerId(getWorkerId()).message(message).build())
                .build();
    }

    private void validateSubmitRequest(TaskRequest request) {
        validateTaskInstanceId(request);
        if (isBlank(request.getPluginType())) {
            throw new IllegalArgumentException("pluginType不能为空");
        }
        if (isBlank(request.getRunMode())) {
            throw new IllegalArgumentException("runMode不能为空");
        }
    }

    private void validateTaskInstanceId(TaskRequest request) {
        if (request == null || isBlank(request.getTaskInstanceId())) {
            throw new IllegalArgumentException("taskInstanceId不能为空");
        }
    }

    private void mergeWorker(Worker target, Worker source) {
        if (!isBlank(source.getId())) {
            target.setId(source.getId());
        }
        if (!isBlank(source.getWorkerCode())) {
            target.setWorkerCode(source.getWorkerCode());
        }
        if (source.getRegisterTime() != null) {
            target.setRegisterTime(source.getRegisterTime());
        }
        if (source.getLastHeartbeatTime() != null) {
            target.setLastHeartbeatTime(source.getLastHeartbeatTime());
        }
        if (source.getUpdateTime() != null) {
            target.setUpdateTime(source.getUpdateTime());
        }
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
