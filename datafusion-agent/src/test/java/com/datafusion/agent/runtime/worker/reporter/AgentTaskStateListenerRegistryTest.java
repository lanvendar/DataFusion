package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerPluginRouter;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentTaskStateListenerRegistry}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class AgentTaskStateListenerRegistryTest {

    /** Test scheduler. */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @AfterEach
    void shutdownScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void shouldNotWriteOrReportWhenMappedStatusIsUnchanged() {
        Fixture fixture = new Fixture(StatusEnum.RUNNING, StatusEnum.RUNNING, true);
        long revision = fixture.store.readState("task-1").orElseThrow().getRevision();
        fixture.registry.register("task-1", StatusEnum.RUNNING);

        fixture.registry.refreshTask("task-1");

        assertEquals(revision, fixture.store.readState("task-1").orElseThrow().getRevision());
        assertTrue(fixture.reporter.results.isEmpty());
    }

    @Test
    void shouldCommitAndReportMappedTerminalState() {
        Fixture fixture = new Fixture(StatusEnum.RUNNING, StatusEnum.RUN_SUCCESS, true);
        fixture.registry.register("task-1", StatusEnum.RUNNING);

        fixture.registry.refreshTask("task-1");

        assertEquals(StatusEnum.RUN_SUCCESS, fixture.store.readState("task-1").orElseThrow().getStatus());
        assertEquals(List.of(StatusEnum.RUN_SUCCESS), fixture.reporter.statuses());
    }

    @Test
    void shouldDiscardMappingWhenRevisionChangesDuringQuery() {
        Fixture fixture = new Fixture(StatusEnum.RUNNING, StatusEnum.RUN_SUCCESS, true);
        fixture.mapping.beforeReturn = () -> {
            WorkerTaskExecutionState control = fixture.store.readState("task-1").orElseThrow();
            control.setStatus(StatusEnum.STOPPING);
            fixture.store.saveState(control, control.getRevision());
        };
        fixture.registry.register("task-1", StatusEnum.RUNNING);

        fixture.registry.refreshTask("task-1");

        assertEquals(StatusEnum.STOPPING, fixture.store.readState("task-1").orElseThrow().getStatus());
        assertEquals(List.of(StatusEnum.STOPPING), fixture.reporter.statuses());
    }

    @Test
    void shouldRetryReportWithoutRewritingState() {
        Fixture fixture = new Fixture(StatusEnum.RUNNING, StatusEnum.RUN_SUCCESS, false);
        fixture.registry.register("task-1", StatusEnum.RUNNING);

        fixture.registry.refreshTask("task-1");
        long revision = fixture.store.readState("task-1").orElseThrow().getRevision();
        fixture.reporter.success = true;
        fixture.registry.refreshTask("task-1");

        assertEquals(revision, fixture.store.readState("task-1").orElseThrow().getRevision());
        assertEquals(2, fixture.reporter.results.size());
    }

    @Test
    void shouldUnregisterAndShutdownListeners() {
        Fixture fixture = new Fixture(StatusEnum.RUNNING, StatusEnum.RUNNING, true);
        fixture.registry.register("task-1", null);
        assertTrue(fixture.registry.isRegistered("task-1"));

        fixture.registry.unregister("task-1");

        assertFalse(fixture.registry.isRegistered("task-1"));
        fixture.registry.shutdown();
        assertEquals(0, fixture.registry.listenerCount());
    }

    /** Registry test fixture. */
    private class Fixture {

        /** Execution store. */
        private final InMemoryWorkerTaskExecutionStore store = new InMemoryWorkerTaskExecutionStore();

        /** State mapping. */
        private final MutableStateMapping mapping;

        /** Reporter. */
        private final RecordingReporter reporter;

        /** Registry under test. */
        private final AgentTaskStateListenerRegistry registry;

        Fixture(StatusEnum initialStatus, StatusEnum mappedStatus, boolean reportSuccess) {
            WorkerTaskExecutionSnap snapshot = WorkerTaskExecutionSnap.builder()
                    .flowInstanceId("flow-1")
                    .taskInstanceId("task-1")
                    .taskName("task")
                    .pluginType("TEST")
                    .runMode("LOCAL")
                    .build();
            store.saveSnapshot(snapshot);
            store.saveState(WorkerTaskExecutionState.builder()
                    .taskInstanceId("task-1")
                    .workerId("worker-1")
                    .workDirPath("/runtime/task-1")
                    .status(initialStatus)
                    .build(), 0L);
            mapping = new MutableStateMapping(mappedStatus);
            reporter = new RecordingReporter(reportSuccess);
            WorkerPluginRouter router = new WorkerPluginRouter(List.of(new TestPluginExecutor()), List.of(mapping));
            registry = new AgentTaskStateListenerRegistry(store, new WorkerTaskStateCoordinator(store),
                    router, reporter, scheduler, 60000L, 2, 60000L, 16);
        }
    }

    /** Mutable test state mapping. */
    private static class MutableStateMapping implements PluginRunModeStateMapping {

        /** Status to return. */
        private final StatusEnum mappedStatus;

        /** Optional concurrent side effect. */
        private Runnable beforeReturn = () -> {
        };

        MutableStateMapping(StatusEnum mappedStatus) {
            this.mappedStatus = mappedStatus;
        }

        @Override
        public String pluginType() {
            return "TEST";
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
            beforeReturn.run();
            return mappedStatus;
        }
    }

    /** Test plugin executor. */
    private static class TestPluginExecutor implements PluginTaskExecutor {

        @Override
        public String pluginType() {
            return "TEST";
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public WorkerResult submit(RunningTaskContext context) {
            return new WorkerResult();
        }

        @Override
        public WorkerResult stop(RunningTaskContext context) {
            return new WorkerResult();
        }

        @Override
        public WorkerResult kill(RunningTaskContext context) {
            return new WorkerResult();
        }

        @Override
        public boolean finish(RunningTaskContext context) {
            return true;
        }
    }

    /** Recording result reporter. */
    private static class RecordingReporter implements TaskResultReporter {

        /** Reported results. */
        private final List<TaskResult> results = new ArrayList<>();

        /** Current report result. */
        private boolean success;

        RecordingReporter(boolean success) {
            this.success = success;
        }

        @Override
        public boolean report(TaskResult result) {
            results.add(result);
            return success;
        }

        List<StatusEnum> statuses() {
            return results.stream().map(TaskResult::getTaskState).toList();
        }
    }
}
