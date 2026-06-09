package com.datafusion.agent.runtime.worker;

import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory worker task execution state store for tests.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public class InMemoryWorkerTaskExecutionStateStore implements WorkerTaskExecutionStateStore {

    /**
     * State records.
     */
    private final Map<String, WorkerTaskExecutionState> records = new ConcurrentHashMap<>();

    @Override
    public void record(WorkerTaskExecutionState state) {
        records.put(state.getTaskInstanceId(), state);
    }

    @Override
    public Optional<WorkerTaskExecutionState> read(String taskInstanceId) {
        return Optional.ofNullable(records.get(taskInstanceId));
    }

    @Override
    public List<WorkerTaskExecutionState> listRecords() {
        return new ArrayList<>(records.values());
    }

    @Override
    public void remove(String taskInstanceId) {
        records.remove(taskInstanceId);
    }
}
