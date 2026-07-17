package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link K8sDataxPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class K8sDataxPluginTaskExecutorTest {

    @Test
    void shouldSubmitAndUpdateCandidateState() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxPluginTaskExecutor executor = executor(client);
        RunningTaskContext context = context(null, null);

        WorkerResult result = executor.submit(context);

        assertEquals(StatusEnum.SUBMIT_SUCCESS, context.getExecutionState().getStatus());
        assertEquals("datax-job", result.getAppId());
    }

    @Test
    void shouldCleanupPreviousRuntimeBeforeResubmit() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxPluginTaskExecutor executor = executor(client);
        WorkerTaskExecutionSnap previousSnapshot = snapshot();
        WorkerTaskExecutionState previousState = state(StatusEnum.RUN_FAILURE);
        previousState.setAppId("old-job");

        executor.submit(context(previousSnapshot, previousState));

        assertEquals(1, client.cleanupCount);
        assertEquals(DataxKubernetesCleanupMode.BEFORE_SUBMIT, client.cleanupMode);
    }

    @Test
    void shouldKeepStoppingAfterStopRequestAccepted() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxPluginTaskExecutor executor = executor(client);
        RunningTaskContext context = context(null, null);
        context.getExecutionState().setStatus(StatusEnum.STOPPING);
        context.getExecutionState().setAppId("datax-job");

        executor.stop(context);

        assertEquals(StatusEnum.STOPPING, context.getExecutionState().getStatus());
        assertEquals(1, client.stopCount);
    }

    @Test
    void shouldCleanupRuntimeOnFinish() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxPluginTaskExecutor executor = executor(client);
        RunningTaskContext context = context(null, null);
        context.getExecutionState().setAppId("datax-job");

        assertTrue(executor.finish(context));
        assertEquals(DataxKubernetesCleanupMode.AFTER_FINISH, client.cleanupMode);
    }

    private K8sDataxPluginTaskExecutor executor(FakeKubernetesClient client) {
        DataxParamResolver resolver = mock(DataxParamResolver.class);
        when(resolver.resolve(any(), any())).thenReturn(param());
        return new K8sDataxPluginTaskExecutor(resolver, client);
    }

    private RunningTaskContext context(WorkerTaskExecutionSnap previousSnapshot,
            WorkerTaskExecutionState previousState) {
        return new RunningTaskContext(snapshot(), state(StatusEnum.SUBMITTING),
                previousSnapshot, previousState, "/runtime/task-1");
    }

    private WorkerTaskExecutionSnap snapshot() {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .pluginType("DATAX")
                .runMode(DataxRunMode.K8S.name())
                .pluginParam(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .status(status)
                .revision(1L)
                .workDirPath("/runtime/task-1")
                .build();
    }

    private DataxExecutionParam param() {
        DataxKubernetesParam kubernetes = DataxKubernetesParam.builder()
                .namespace("default")
                .jobName("datax-job")
                .secretName("datax-secret")
                .podLabelSelector("task=task-1")
                .containerName("datax")
                .collectLogsOnFinish(true)
                .deleteJobOnFinish(false)
                .build();
        return DataxExecutionParam.builder()
                .taskInstanceId("task-1")
                .runMode(DataxRunMode.K8S)
                .workDir(Path.of("/runtime/task-1"))
                .kubernetes(kubernetes)
                .build();
    }

    /** Fake Kubernetes client. */
    private static class FakeKubernetesClient implements DataxKubernetesClient {

        /** Cleanup count. */
        private int cleanupCount;

        /** Cleanup mode. */
        private DataxKubernetesCleanupMode cleanupMode;

        /** Stop count. */
        private int stopCount;

        @Override
        public DataxKubernetesRuntimeRef submit(DataxExecutionParam param) {
            return DataxKubernetesRuntimeRef.builder()
                    .namespace("default")
                    .jobName("datax-job")
                    .build();
        }

        @Override
        public boolean cleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode) {
            cleanupCount++;
            cleanupMode = mode;
            return true;
        }

        @Override
        public void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
            stopCount++;
        }

        @Override
        public DataxKubernetesStatus queryStatus(DataxKubernetesRuntimeRef runtimeRef) {
            return null;
        }

        @Override
        public String collectLogs(DataxKubernetesRuntimeRef runtimeRef) {
            return null;
        }
    }
}
