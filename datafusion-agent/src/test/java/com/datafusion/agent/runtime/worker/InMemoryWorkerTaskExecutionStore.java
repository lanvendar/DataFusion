package com.datafusion.agent.runtime.worker;

import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory worker task execution store for tests.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public class InMemoryWorkerTaskExecutionStore implements WorkerTaskExecutionStore {

    /** State records. */
    private final Map<String, WorkerTaskExecutionState> states = new ConcurrentHashMap<>();

    /** Snapshot records. */
    private final Map<String, WorkerTaskExecutionSnap> snapshots = new ConcurrentHashMap<>();

    @Override
    public String saveSnapshot(WorkerTaskExecutionSnap snapshot) {
        snapshots.put(snapshot.getTaskInstanceId(), snapshot.copy());
        return "/runtime/" + snapshot.getTaskInstanceId();
    }

    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        return Optional.ofNullable(snapshots.get(taskInstanceId)).map(WorkerTaskExecutionSnap::copy);
    }

    @Override
    public synchronized boolean saveState(WorkerTaskExecutionState state, long expectedRevision) {
        WorkerTaskExecutionState current = states.get(state.getTaskInstanceId());
        long currentRevision = current == null ? 0L : current.getRevision();
        if (currentRevision != expectedRevision) {
            return false;
        }
        WorkerTaskExecutionState persisted = state.copy();
        persisted.setRevision(currentRevision + 1L);
        states.put(state.getTaskInstanceId(), persisted);
        state.setRevision(persisted.getRevision());
        return true;
    }

    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        return Optional.ofNullable(states.get(taskInstanceId)).map(WorkerTaskExecutionState::copy);
    }

    @Override
    public Set<String> restoreExecutions(Collection<String> taskInstanceIds) {
        Set<String> restored = new HashSet<>();
        if (taskInstanceIds == null) {
            return restored;
        }
        taskInstanceIds.stream()
                .filter(taskInstanceId -> snapshots.containsKey(taskInstanceId) && states.containsKey(taskInstanceId))
                .forEach(restored::add);
        return restored;
    }

    @Override
    public void deleteExecution(String taskInstanceId) {
        states.remove(taskInstanceId);
        snapshots.remove(taskInstanceId);
    }
}
