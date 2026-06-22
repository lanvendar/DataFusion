package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        stateStore.saveState(state(StatusEnum.STOP_SUCCESS));
        RecordingTaskResultReporter reporter = new RecordingTaskResultReporter(true);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentTaskStateReportScheduler reportScheduler = new AgentTaskStateReportScheduler(stateStore, reporter,
                scheduler, Collections.emptyList(), 1000L, 1);

        refreshStates(reportScheduler);

        assertEquals(1, reporter.reportCount);
        assertTrue(stateStore.listListeningStates().isEmpty());
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
}
