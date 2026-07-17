package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DataxPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class DataxPluginTaskExecutorTest {

    @Test
    void shouldResolveSnapshotAndUpdateActionCandidate() {
        DataxParamResolver resolver = mock(DataxParamResolver.class);
        when(resolver.resolve(any(), any())).thenReturn(DataxExecutionParam.builder().build());
        TestDataxExecutor executor = new TestDataxExecutor(resolver);
        RunningTaskContext context = context();

        WorkerResult result = executor.submit(context);

        assertEquals(StatusEnum.SUBMIT_SUCCESS, context.getExecutionState().getStatus());
        assertEquals("app-1", result.getAppId());
        assertEquals("/runtime/task-1", result.getWorkDirPath());
    }

    private RunningTaskContext context() {
        WorkerTaskExecutionSnap snapshot = WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .pluginType("DATAX")
                .runMode("K8S")
                .build();
        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .status(StatusEnum.SUBMITTING)
                .revision(1L)
                .build();
        return new RunningTaskContext(snapshot, state, null, null, "/runtime/task-1");
    }

    /** Test DataX executor. */
    private static class TestDataxExecutor extends DataxPluginTaskExecutor {

        TestDataxExecutor(DataxParamResolver resolver) {
            super(resolver);
        }

        @Override
        public String runMode() {
            return DataxRunMode.K8S.name();
        }

        @Override
        protected WorkerResult submit(RunningTaskContext context, DataxExecutionParam param) {
            applyResult(context.getExecutionState(), StatusEnum.SUBMIT_SUCCESS, "app-1",
                    context.getWorkDirPath(), null);
            return workerResult(context.getExecutionState());
        }

        @Override
        protected WorkerResult stop(RunningTaskContext context, DataxExecutionParam param) {
            return new WorkerResult();
        }

        @Override
        protected WorkerResult kill(RunningTaskContext context, DataxExecutionParam param) {
            return new WorkerResult();
        }
    }
}
