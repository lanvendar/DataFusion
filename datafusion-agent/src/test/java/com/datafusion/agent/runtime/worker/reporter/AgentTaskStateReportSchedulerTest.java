package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentTaskStateReportScheduler}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/22
 * @since 1.0.0
 */
class AgentTaskStateReportSchedulerTest {

    @Test
    void shouldRemoveFinalStateAfterSuccessfulReport() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveSnapshot(snapshot());
        stateStore.saveState(state(StatusEnum.RUN_SUCCESS));
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentTaskStateReportScheduler reportScheduler = new AgentTaskStateReportScheduler(stateStore, reporter,
                scheduler, Collections.emptyList(), 1000L, 1);

        refreshStates(reportScheduler);

        assertEquals(1, reporter.reportCount);
        assertTrue(stateStore.listListeningStates().isEmpty());
        assertTrue(stateStore.readState("task-1").isEmpty());
        assertTrue(stateStore.readSnapshot("task-1").isEmpty());
        scheduler.shutdownNow();
    }

    @Test
    void shouldStopListeningAndKeepFailedFinalStateAfterSuccessfulReport() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveSnapshot(snapshot());
        stateStore.saveState(state(StatusEnum.RUN_FAILURE));
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentTaskStateReportScheduler reportScheduler = new AgentTaskStateReportScheduler(stateStore, reporter,
                scheduler, Collections.emptyList(), 1000L, 1);

        refreshStates(reportScheduler);

        assertEquals(1, reporter.reportCount);
        assertTrue(stateStore.listListeningStates().isEmpty());
        assertTrue(stateStore.readState("task-1").isPresent());
        assertTrue(stateStore.readSnapshot("task-1").isPresent());
        scheduler.shutdownNow();
    }

    @Test
    void shouldKeepFinalStateWhenReportFails() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveSnapshot(snapshot());
        stateStore.saveState(state(StatusEnum.STOP_SUCCESS));
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(false);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentTaskStateReportScheduler reportScheduler = new AgentTaskStateReportScheduler(stateStore, reporter,
                scheduler, Collections.emptyList(), 1000L, 1);

        refreshStates(reportScheduler);

        assertEquals(1, reporter.reportCount);
        assertEquals(1, stateStore.listListeningStates().size());
        assertFalse(stateStore.readState("task-1").isEmpty());
        scheduler.shutdownNow();
    }

    @Test
    void shouldNotMapSubmittingStateWithoutAppIdToUnknown() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveSnapshot(snapshot());
        stateStore.saveState(state(StatusEnum.SUBMITTING));
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentTaskStateReportScheduler reportScheduler = new AgentTaskStateReportScheduler(stateStore, reporter,
                scheduler, List.of(new UnknownStateMapping()), 1000L, 1);

        refreshStates(reportScheduler);

        assertEquals(0, reporter.reportCount);
        assertEquals(StatusEnum.SUBMITTING, stateStore.readState("task-1").orElseThrow().getStatus());
        scheduler.shutdownNow();
    }

    @Test
    void shouldStopListeningAfterUnknownStateReportedSuccessfully() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveSnapshot(snapshot());
        WorkerTaskExecutionState state = state(StatusEnum.RUNNING);
        state.setAppId("app-1");
        stateStore.saveState(state);
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentTaskStateReportScheduler reportScheduler = new AgentTaskStateReportScheduler(stateStore, reporter,
                scheduler, List.of(new UnknownStateMapping()), 1000L, 1);

        refreshStates(reportScheduler);
        refreshStates(reportScheduler);

        assertEquals(1, reporter.reportCount);
        assertTrue(stateStore.listListeningStates().isEmpty());
        assertEquals(StatusEnum.UNKNOWN, stateStore.readState("task-1").orElseThrow().getStatus());
        assertTrue(stateStore.readSnapshot("task-1").isPresent());
        scheduler.shutdownNow();
    }

    private void refreshStates(AgentTaskStateReportScheduler scheduler) throws Exception {
        Method method = AgentTaskStateReportScheduler.class.getDeclaredMethod("refreshStates");
        method.setAccessible(true);
        method.invoke(scheduler);
    }

    private WorkerTaskExecutionSnap snapshot() {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("task-name")
                .pluginType("SHELL")
                .runMode("LOCAL")
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .status(status)
                .build();
    }

    /**
     * Recording task result reporter.
     */
    private static class RecordingTaskResultReporter implements TaskResultReporter {

        /**
         * Report result.
         */
        private final boolean result;

        /**
         * Report count.
         */
        private int reportCount;

        RecordingTaskResultReporter(boolean result) {
            this.result = result;
        }

        @Override
        public boolean report(TaskResult result) {
            reportCount++;
            return this.result;
        }
    }

    /**
     * Unknown state mapping.
     */
    private static class UnknownStateMapping implements PluginRunModeStateMapping {

        @Override
        public String pluginType() {
            return "SHELL";
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public StatusEnum mapState(WorkerTaskExecutionState state) {
            return StatusEnum.UNKNOWN;
        }
    }
}
