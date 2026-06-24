package com.datafusion.agent.runtime.worker;

import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory worker task execution state store for tests.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public class InMemoryWorkerTaskExecutionStore implements WorkerTaskExecutionStore {

    /**
     * State records.
     */
    private final Map<String, WorkerTaskExecutionState> records = new ConcurrentHashMap<>();

    /**
     * Snapshot records.
     */
    private final Map<String, WorkerTaskExecutionSnap> snapshots = new ConcurrentHashMap<>();

    /**
     * Listening task IDs.
     */
    private final Map<String, Boolean> listeningRecords = new ConcurrentHashMap<>();

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
        records.put(state.getTaskInstanceId(), state);
        listeningRecords.put(state.getTaskInstanceId(), Boolean.TRUE);
    }

    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        return Optional.ofNullable(records.get(taskInstanceId));
    }

    @Override
    public List<WorkerTaskExecutionState> listListeningStates() {
        return listeningRecords.keySet()
                .stream()
                .map(records::get)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void remove(String taskInstanceId) {
        records.remove(taskInstanceId);
        snapshots.remove(taskInstanceId);
        listeningRecords.remove(taskInstanceId);
    }

    @Override
    public void stopListening(String taskInstanceId) {
        listeningRecords.remove(taskInstanceId);
    }
}
