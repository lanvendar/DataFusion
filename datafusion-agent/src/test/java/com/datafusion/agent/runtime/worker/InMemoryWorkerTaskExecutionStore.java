package com.datafusion.agent.runtime.worker;

import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory worker task execution state store for tests.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public class InMemoryWorkerTaskExecutionStore implements WorkerTaskExecutionStore, WorkerTaskContextStorage {

    /**
     * State records.
     */
    private final Map<String, WorkerTaskExecutionState> records = new ConcurrentHashMap<>();

    /**
     * Snapshot records.
     */
    private final Map<String, WorkerTaskExecutionSnap> snapshots = new ConcurrentHashMap<>();

    /**
     * Context records.
     */
    private final Map<String, RunningTaskContext> contexts = new ConcurrentHashMap<>();

    /**
     * Task locks.
     */
    private final Map<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>();

    @Override
    public void saveSnapshot(WorkerTaskExecutionSnap snapshot) {
        snapshots.put(snapshot.getTaskInstanceId(), snapshot);
    }

    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        return Optional.ofNullable(snapshots.get(taskInstanceId));
    }

    @Override
    public void saveState(WorkerTaskExecutionState state) {
        withTaskLock(state.getTaskInstanceId(), () -> {
            WorkerTaskExecutionState oldState = records.get(state.getTaskInstanceId());
            state.setRevision(oldState == null ? 1L : oldState.getRevision() + 1L);
            records.put(state.getTaskInstanceId(), state);
            contexts.compute(state.getTaskInstanceId(), (taskInstanceId, context) -> {
                RunningTaskContext current = context == null ? new RunningTaskContext() : context;
                current.setTaskInstanceId(taskInstanceId);
                current.setWorkerId(state.getWorkerId());
                current.setAppId(state.getAppId());
                current.setWorkDirPath(state.getWorkDirPath());
                current.setTaskState(state.getStatus());
                current.setResult(state.getResult());
                return current;
            });
            return null;
        });
    }

    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        return Optional.ofNullable(records.get(taskInstanceId));
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        return withTaskLock(taskInstanceId, () -> contexts.get(taskInstanceId));
    }

    @Override
    public RunningTaskContext getOrCreate(TaskRequest request) {
        return withTaskLock(request.getTaskInstanceId(), () -> contexts.computeIfAbsent(request.getTaskInstanceId(),
                taskInstanceId -> RunningTaskContext.fromRequest(request)));
    }

    @Override
    public void save(RunningTaskContext context) {
        withTaskLock(context.getTaskInstanceId(), () -> {
            readState(context.getTaskInstanceId()).ifPresent(existing -> {
                if (context.getAppId() == null) {
                    context.setAppId(existing.getAppId());
                }
                if (context.getWorkerId() == null) {
                    context.setWorkerId(existing.getWorkerId());
                }
                if (context.getWorkDirPath() == null) {
                    context.setWorkDirPath(existing.getWorkDirPath());
                }
            });
            contexts.put(context.getTaskInstanceId(), context);
            saveSnapshot(context.getSnapshot());
            saveState(context.getExecutionState());
            return null;
        });
    }

    @Override
    public void deleteExecution(String taskInstanceId) {
        withTaskLock(taskInstanceId, () -> {
            records.remove(taskInstanceId);
            snapshots.remove(taskInstanceId);
            contexts.remove(taskInstanceId);
            return null;
        });
    }

    @Override
    public void removeContext(String taskInstanceId) {
        withTaskLock(taskInstanceId, () -> contexts.remove(taskInstanceId));
    }

    @Override
    public <T> T withTaskLock(String taskInstanceId, Supplier<T> action) {
        if (taskInstanceId == null) {
            return action.get();
        }
        ReentrantLock lock = taskLocks.computeIfAbsent(taskInstanceId, key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
