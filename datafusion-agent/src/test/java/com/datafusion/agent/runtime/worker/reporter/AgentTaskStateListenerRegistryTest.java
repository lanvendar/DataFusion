package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentTaskStateListenerRegistry}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/16
 * @since 1.0.0
 */
class AgentTaskStateListenerRegistryTest {

    /**
     * Test schedulers.
     */
    private final Deque<ScheduledExecutorService> schedulers = new ArrayDeque<>();

    @AfterEach
    void shutdownSchedulers() {
        schedulers.forEach(ScheduledExecutorService::shutdownNow);
    }

    @Test
    void shouldRejectDuplicatePluginRunModeMapping() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);

        assertThrows(IllegalArgumentException.class, () -> registry(stateStore,
                new RecordingTaskResultReporter(true),
                List.of(new FixedStateMapping(StatusEnum.RUNNING), new FixedStateMapping(StatusEnum.RUN_SUCCESS)),
                60000L, 10));
    }

    @Test
    void shouldNotReportWhenMappedStateIsUnchanged() {
        LockCountingTaskExecutionStore stateStore = new LockCountingTaskExecutionStore();
        saveTask(stateStore, "task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.RUNNING)), 60000L, 10);

        registry.register("task-1");
        stateStore.resetLockCount();
        registry.refreshTask("task-1");

        assertEquals(0, reporter.reportCount);
        assertEquals(0, stateStore.lockCount);
        assertEquals(StatusEnum.RUNNING, stateStore.readState("task-1").orElseThrow().getStatus());
        assertTrue(registry.isRegistered("task-1"));
    }

    @Test
    void shouldReportLocalStateChangeFromAsyncSubmit() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.SUBMITTING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.SUBMIT_SUCCESS)), 60000L, 10);
        registry.register("task-1");
        saveTask(stateStore, "task-1", StatusEnum.SUBMIT_SUCCESS);

        registry.refreshTask("task-1");

        assertEquals(1, reporter.reportCount);
        assertEquals(StatusEnum.SUBMIT_SUCCESS, stateStore.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldNotReportSameStatusWrite() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.RUNNING)), 60000L, 10);
        registry.register("task-1");
        saveTask(stateStore, "task-1", StatusEnum.RUNNING);

        registry.refreshTask("task-1");

        assertEquals(0, reporter.reportCount);
        assertEquals(2L, stateStore.readState("task-1").orElseThrow().getRevision());
    }

    @Test
    void shouldPersistAndReportChangedStateOnce() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.RUN_SUCCESS)), 60000L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");
        registry.refreshTask("task-1");

        assertEquals(1, reporter.reportCount);
        assertEquals(StatusEnum.RUN_SUCCESS, stateStore.readState("task-1").orElseThrow().getStatus());
        assertTrue(registry.isRegistered("task-1"));
    }

    @Test
    void shouldRetryPendingReportWithoutAnotherStateChange() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(false, true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.RUN_FAILURE)), 60000L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");
        registry.refreshTask("task-1");

        assertEquals(2, reporter.reportCount);
        assertEquals(StatusEnum.RUN_FAILURE, stateStore.readState("task-1").orElseThrow().getStatus());
        assertTrue(registry.isRegistered("task-1"));
    }

    @Test
    void shouldPersistPreparedFinalResultOnce() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(false, true);
        FinalizingStateMapping mapping = new FinalizingStateMapping();
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter, List.of(mapping), 60000L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");
        registry.refreshTask("task-1");

        WorkerTaskExecutionState state = stateStore.readState("task-1").orElseThrow();
        assertEquals(2, reporter.reportCount);
        assertEquals(2, mapping.prepareCount);
        assertEquals(2L, state.getRevision());
        assertTrue(state.getResult().path("finalized").asBoolean());
    }

    @Test
    void shouldNotEvictTerminalListenerWhileReportIsPending() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        AgentTaskStateListenerRegistry registry = registry(stateStore, new RecordingTaskResultReporter(false),
                List.of(new FixedStateMapping(StatusEnum.RUN_FAILURE)), 0L, 0);

        registry.register("task-1");
        registry.refreshTask("task-1");

        assertTrue(registry.isRegistered("task-1"));
    }

    @Test
    void shouldReleaseTaskLockBeforeReporting() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        StateChangingReporter reporter = new StateChangingReporter(stateStore);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.RUN_SUCCESS)), 60000L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");

        assertTrue(reporter.stateChanged);
        assertEquals(StatusEnum.STOPPING, stateStore.readState("task-1").orElseThrow().getStatus());
        assertTrue(registry.isRegistered("task-1"));
    }

    @Test
    void shouldDiscardMappedStateWhenControlStateChangedDuringQuery() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new StateChangingMapping(stateStore)), 60000L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");

        assertEquals(0, reporter.reportCount);
        assertEquals(StatusEnum.STOPPING, stateStore.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldDiscardMappedStateWithoutLockWhenRevisionChangedDuringQuery() {
        LockCountingTaskExecutionStore stateStore = new LockCountingTaskExecutionStore();
        saveTask(stateStore, "task-1", StatusEnum.RUNNING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new RevisionChangingMapping(stateStore)), 60000L, 10);

        registry.register("task-1");
        stateStore.resetLockCount();
        registry.refreshTask("task-1");

        WorkerTaskExecutionState state = stateStore.readState("task-1").orElseThrow();
        assertEquals(0, reporter.reportCount);
        assertEquals(1, stateStore.lockCount);
        assertEquals(StatusEnum.RUNNING, state.getStatus());
        assertEquals(2L, state.getRevision());
    }

    @Test
    void shouldRejectRuntimeResultThatOverridesStoppingIntent() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.STOPPING);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter,
                List.of(new FixedStateMapping(StatusEnum.RUN_SUCCESS)), 60000L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");

        assertEquals(0, reporter.reportCount);
        assertEquals(StatusEnum.STOPPING, stateStore.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldReportRestoredFinalStateOnce() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUN_FAILURE);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        AgentTaskStateListenerRegistry registry = registry(stateStore, reporter, Collections.emptyList(), 60000L, 10);
        TaskRequest request = new TaskRequest();
        request.setTaskInstanceId("task-1");

        registry.restoreTasks(List.of(request));
        registry.refreshTask("task-1");
        registry.refreshTask("task-1");

        assertEquals(1, reporter.reportCount);
        assertTrue(registry.isRegistered("task-1"));
    }

    @Test
    void shouldRestoreOnlyManagerTasksThatHaveLocalState() {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUNNING);
        AgentTaskStateListenerRegistry registry = registry(stateStore, new RecordingTaskResultReporter(true),
                List.of(new FixedStateMapping(StatusEnum.RUNNING)), 60000L, 10);
        TaskRequest localTask = new TaskRequest();
        localTask.setTaskInstanceId("task-1");
        TaskRequest missingTask = new TaskRequest();
        missingTask.setTaskInstanceId("task-2");

        registry.restoreTasks(List.of(localTask, missingTask));

        assertEquals(1, registry.listenerCount());
        assertTrue(registry.isRegistered("task-1"));
        assertFalse(registry.isRegistered("task-2"));
    }

    @Test
    void shouldEvictTerminalListenerImmediatelyWhenRetentionIsZero() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = stateStore("task-1", StatusEnum.RUN_FAILURE);
        AgentTaskStateListenerRegistry registry = registry(stateStore, new RecordingTaskResultReporter(true),
                Collections.emptyList(), 0L, 10);

        registry.register("task-1");
        registry.refreshTask("task-1");

        awaitRegistration(registry, "task-1", false);
    }

    @Test
    void shouldOnlyCountRetainedTerminalListenersForCapacityEviction() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        saveTask(stateStore, "task-1", StatusEnum.RUN_FAILURE);
        saveTask(stateStore, "task-2", StatusEnum.STOP_FAILURE);
        saveTask(stateStore, "task-3", StatusEnum.RUNNING);
        AgentTaskStateListenerRegistry registry = registry(stateStore, new RecordingTaskResultReporter(true),
                List.of(new FixedStateMapping(StatusEnum.RUNNING)), 60000L, 1);

        registry.register("task-1");
        registry.register("task-2");
        registry.register("task-3");
        registry.refreshTask("task-1");
        registry.refreshTask("task-2");
        registry.refreshTask("task-3");

        awaitRegistration(registry, "task-1", false);
        assertEquals(2, registry.listenerCount());
        assertTrue(registry.isRegistered("task-3"));
    }

    private void awaitRegistration(AgentTaskStateListenerRegistry registry, String taskInstanceId,
            boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 1000L;
        while (registry.isRegistered(taskInstanceId) != expected && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertEquals(expected, registry.isRegistered(taskInstanceId));
    }

    private AgentTaskStateListenerRegistry registry(InMemoryWorkerTaskExecutionStore stateStore,
            TaskResultReporter reporter, List<PluginRunModeStateMapping> mappings, long retentionMs,
            int retentionNum) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        schedulers.add(scheduler);
        return new AgentTaskStateListenerRegistry(stateStore, reporter, scheduler, mappings, 60000L, 1,
                retentionMs, retentionNum);
    }

    private InMemoryWorkerTaskExecutionStore stateStore(String taskInstanceId, StatusEnum status) {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        saveTask(stateStore, taskInstanceId, status);
        return stateStore;
    }

    private void saveTask(InMemoryWorkerTaskExecutionStore stateStore, String taskInstanceId, StatusEnum status) {
        stateStore.saveSnapshot(WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId(taskInstanceId)
                .taskName("task-name")
                .pluginType("SHELL")
                .runMode("LOCAL")
                .build());
        stateStore.saveState(WorkerTaskExecutionState.builder()
                .taskInstanceId(taskInstanceId)
                .appId("app-1")
                .status(status)
                .build());
    }

    /**
     * Task execution store that counts explicit task-lock sections.
     */
    private static class LockCountingTaskExecutionStore extends InMemoryWorkerTaskExecutionStore {

        /**
         * Task-lock acquisition count.
         */
        private int lockCount;

        @Override
        public <T> T withTaskLock(String taskInstanceId, java.util.function.Supplier<T> action) {
            lockCount++;
            return super.withTaskLock(taskInstanceId, action);
        }

        private void resetLockCount() {
            lockCount = 0;
        }
    }

    /**
     * Recording task result reporter.
     */
    private static class RecordingTaskResultReporter implements TaskResultReporter {

        /**
         * Report results.
         */
        private final Deque<Boolean> results;

        /**
         * Report count.
         */
        private int reportCount;

        RecordingTaskResultReporter(Boolean... results) {
            this.results = new ArrayDeque<>(Arrays.asList(results));
        }

        @Override
        public boolean report(TaskResult result) {
            reportCount++;
            return results.size() > 1 ? results.removeFirst() : results.getFirst();
        }
    }

    /**
     * Reporter that changes local state from another thread.
     */
    private static class StateChangingReporter implements TaskResultReporter {

        /**
         * Task execution store.
         */
        private final InMemoryWorkerTaskExecutionStore stateStore;

        /**
         * Whether the state change completed while reporting.
         */
        private boolean stateChanged;

        StateChangingReporter(InMemoryWorkerTaskExecutionStore stateStore) {
            this.stateStore = stateStore;
        }

        @Override
        public boolean report(TaskResult result) {
            try {
                CompletableFuture.runAsync(() -> stateStore.saveState(WorkerTaskExecutionState.builder()
                                .taskInstanceId(result.getTaskInstanceId())
                                .appId(result.getWorkerResult().getAppId())
                                .status(StatusEnum.STOPPING)
                                .build()))
                        .get(1L, TimeUnit.SECONDS);
                stateChanged = true;
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * Fixed state mapping.
     */
    private static class FixedStateMapping implements PluginRunModeStateMapping {

        /**
         * Mapped status.
         */
        private final StatusEnum mappedStatus;

        FixedStateMapping(StatusEnum mappedStatus) {
            this.mappedStatus = mappedStatus;
        }

        @Override
        public String pluginType() {
            return "SHELL";
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
            return mappedStatus;
        }
    }

    /**
     * Mapping that prepares a final result once.
     */
    private static class FinalizingStateMapping extends FixedStateMapping {

        /**
         * Prepare count.
         */
        private int prepareCount;

        FinalizingStateMapping() {
            super(StatusEnum.RUN_SUCCESS);
        }

        @Override
        public boolean prepareFinalReport(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
            prepareCount++;
            if (state.getResult() != null && state.getResult().path("finalized").asBoolean()) {
                return false;
            }
            state.setResult(JsonNodeFactory.instance.objectNode().put("finalized", true));
            return true;
        }
    }

    /**
     * Mapping that simulates a concurrent stop request.
     */
    private static class StateChangingMapping extends FixedStateMapping {

        /**
         * Task execution store.
         */
        private final InMemoryWorkerTaskExecutionStore stateStore;

        StateChangingMapping(InMemoryWorkerTaskExecutionStore stateStore) {
            super(StatusEnum.RUN_SUCCESS);
            this.stateStore = stateStore;
        }

        @Override
        public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
            stateStore.saveState(WorkerTaskExecutionState.builder()
                    .taskInstanceId(state.getTaskInstanceId())
                    .appId(state.getAppId())
                    .status(StatusEnum.STOPPING)
                    .build());
            return super.mapState(snapshot, state);
        }
    }

    /**
     * Mapping that simulates a same-state write during a query.
     */
    private static class RevisionChangingMapping extends FixedStateMapping {

        /**
         * Task execution store.
         */
        private final InMemoryWorkerTaskExecutionStore stateStore;

        RevisionChangingMapping(InMemoryWorkerTaskExecutionStore stateStore) {
            super(StatusEnum.RUN_SUCCESS);
            this.stateStore = stateStore;
        }

        @Override
        public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
            stateStore.saveState(WorkerTaskExecutionState.builder()
                    .taskInstanceId(state.getTaskInstanceId())
                    .appId(state.getAppId())
                    .status(state.getStatus())
                    .build());
            return super.mapState(snapshot, state);
        }
    }
}
