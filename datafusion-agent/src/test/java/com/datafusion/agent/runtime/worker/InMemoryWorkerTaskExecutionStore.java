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
        contexts.compute(snapshot.getTaskInstanceId(), (taskInstanceId, context) -> {
            RunningTaskContext current = context == null ? new RunningTaskContext() : context;
            current.setTaskInstanceId(taskInstanceId);
            current.setSnapshot(snapshot);
            return current;
        });
    }

    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        return Optional.ofNullable(snapshots.get(taskInstanceId));
    }

    @Override
    public boolean saveState(WorkerTaskExecutionState state, long expectedRevision) {
        return withTaskLock(state.getTaskInstanceId(), () -> {
            WorkerTaskExecutionState oldState = records.get(state.getTaskInstanceId());
            long currentRevision = oldState == null ? 0L : oldState.getRevision();
            if (currentRevision != expectedRevision) {
                return false;
            }
            if (oldState != null) {
                if (state.getAppId() == null) {
                    state.setAppId(oldState.getAppId());
                }
                if (state.getWorkerId() == null) {
                    state.setWorkerId(oldState.getWorkerId());
                }
                if (state.getWorkDirPath() == null) {
                    state.setWorkDirPath(oldState.getWorkDirPath());
                }
                if (state.getExitCode() == null) {
                    state.setExitCode(oldState.getExitCode());
                }
            }
            state.setRevision(currentRevision + 1L);
            records.put(state.getTaskInstanceId(), state);
            contexts.compute(state.getTaskInstanceId(), (taskInstanceId, context) -> {
                RunningTaskContext current = context == null ? new RunningTaskContext() : context;
                current.setTaskInstanceId(taskInstanceId);
                current.setExecutionState(state);
                return current;
            });
            return true;
        });
    }

    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        return Optional.ofNullable(records.get(taskInstanceId));
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        return contexts.get(taskInstanceId);
    }

    @Override
    public RunningTaskContext getOrCreate(TaskRequest request) {
        return contexts.computeIfAbsent(request.getTaskInstanceId(),
                taskInstanceId -> RunningTaskContext.fromRequest(request));
    }

    @Override
    public void save(RunningTaskContext context) {
        contexts.put(context.getTaskInstanceId(), context);
        saveSnapshot(context.getSnapshot());
        WorkerTaskExecutionState state = context.getExecutionState();
        if (state != null) {
            saveState(state, state.getRevision());
        }
    }

    @Override
    public void deleteExecution(String taskInstanceId) {
        records.remove(taskInstanceId);
        contexts.remove(taskInstanceId);
        snapshots.remove(taskInstanceId);
    }

    @Override
    public void removeContext(String taskInstanceId) {
        contexts.remove(taskInstanceId);
    }

    private <T> T withTaskLock(String taskInstanceId, Supplier<T> action) {
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
