package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxTaskResult;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link K8sDataxTaskRunner}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
class K8sDataxTaskRunnerTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void shouldReturnStoppingBeforeKubernetesJobActuallyExits() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxTaskRunner runner = runner(client);

        DataxTaskResult result = runner.stop(param(), state(StatusEnum.RUNNING));

        assertEquals(StatusEnum.STOPPING, result.getStatus());
        assertEquals(false, client.forcibly);
    }

    @Test
    void shouldReturnKilledWhenKubernetesKillDeletesJob() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxTaskRunner runner = runner(client);

        DataxTaskResult result = runner.kill(param(), state(StatusEnum.UNKNOWN));

        assertEquals(StatusEnum.KILLED, result.getStatus());
        assertEquals(true, client.forcibly);
        assertEquals("df-datax-task-1", client.lastRuntimeRef.getJobName());
    }

    @Test
    void shouldReturnUnknownWhenKubernetesKillFails() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        client.stopException = new IllegalStateException("delete failed");
        K8sDataxTaskRunner runner = runner(client);

        DataxTaskResult result = runner.kill(param(), state(StatusEnum.UNKNOWN));

        assertEquals(StatusEnum.UNKNOWN, result.getStatus());
        assertEquals("K8S DataX kill failed: delete failed", result.getResult().path("message").asText());
    }

    @Test
    void shouldReturnKilledWhenKubernetesRuntimeRefIsMissingOnKill() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxTaskRunner runner = runner(client);

        DataxTaskResult result = runner.kill(param(), stateWithoutAppId(StatusEnum.UNKNOWN));

        assertEquals(StatusEnum.KILLED, result.getStatus());
        assertNull(client.forcibly);
    }

    @Test
    void shouldCleanupOldKubernetesJobBeforeSubmit() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveState(state(StatusEnum.RUN_FAILURE));
        K8sDataxTaskRunner runner = runner(client, stateStore);

        DataxTaskResult result = runner.submit(param());

        assertEquals(StatusEnum.RUNNING, result.getStatus());
        assertEquals(1, client.cleanupCount);
        assertEquals(DataxKubernetesCleanupMode.BEFORE_SUBMIT, client.lastCleanupMode);
        assertEquals(1, client.submitCount);
        assertEquals("df-datax-task-1", client.lastRuntimeRef.getJobName());
    }

    @Test
    void shouldReturnSubmitFailureWhenOldKubernetesCleanupFails() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        client.cleanupResult = false;
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveState(state(StatusEnum.RUN_FAILURE));
        K8sDataxTaskRunner runner = runner(client, stateStore);

        DataxTaskResult result = runner.submit(param());

        assertEquals(StatusEnum.SUBMIT_FAILURE, result.getStatus());
        assertEquals(1, client.cleanupCount);
        assertEquals(DataxKubernetesCleanupMode.BEFORE_SUBMIT, client.lastCleanupMode);
        assertEquals(0, client.submitCount);
        assertEquals("df-datax-task-1", client.lastRuntimeRef.getJobName());
        assertTrue(result.getResult().path("message").asText().contains("cleanup before submit unfinished"));
    }

    @Test
    void shouldCleanupKubernetesRuntimeOnFinish() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxTaskRunner runner = runner(client);

        boolean result = runner.finish(param(), state(StatusEnum.RUN_SUCCESS));

        assertTrue(result);
        assertEquals(1, client.cleanupCount);
        assertEquals(DataxKubernetesCleanupMode.AFTER_FINISH, client.lastCleanupMode);
        assertEquals("df-datax-task-1", client.lastRuntimeRef.getJobName());
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setModules(tempDir.toString());
        properties.getStorage().setTaskRuntimeDir(tempDir.resolve("task-runtime").toString());
        return properties;
    }

    private K8sDataxTaskRunner runner(FakeKubernetesClient client) {
        return runner(client, new InMemoryWorkerTaskExecutionStore());
    }

    private K8sDataxTaskRunner runner(FakeKubernetesClient client, InMemoryWorkerTaskExecutionStore stateStore) {
        return new K8sDataxTaskRunner(client, stateStore);
    }

    private TaskRequest request() {
        return request(false);
    }

    private TaskRequest request(boolean collectLogsOnFinish) {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode pluginKubernetes = OBJECT_MAPPER.createObjectNode();
        pluginKubernetes.put("namespace", "df");
        pluginKubernetes.put("image", "datafusion/datax:latest");
        pluginKubernetes.put("collectLogsOnFinish", collectLogsOnFinish);
        pluginParam.set("kubernetes", pluginKubernetes);
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.set("jobJson", OBJECT_MAPPER.createObjectNode());
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("DataX");
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);
        return request;
    }

    private DataxExecutionParam param() {
        return new DataxParamResolver(properties()).resolve(request());
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-datax-task-1")
                .workDirPath(tempDir.resolve("task-runtime").resolve("20260621").resolve("flow-1")
                        .resolve("task-1").toString())
                .status(status)
                .build();
    }

    private WorkerTaskExecutionState stateWithoutAppId(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .workDirPath(tempDir.resolve("task-runtime").resolve("20260621").resolve("flow-1")
                        .resolve("task-1").toString())
                .status(status)
                .build();
    }

    /**
     * Fake Kubernetes client.
     */
    private static class FakeKubernetesClient implements DataxKubernetesClient {

        /**
         * Last force-delete flag.
         */
        private Boolean forcibly;

        /**
         * Query status.
         */
        private StatusEnum status = StatusEnum.RUNNING;

        /**
         * Cleanup resource count.
         */
        private int cleanupCount;

        /**
         * Submit count.
         */
        private int submitCount;

        /**
         * Cleanup resource result.
         */
        private boolean cleanupResult = true;

        /**
         * Last cleanup mode.
         */
        private DataxKubernetesCleanupMode lastCleanupMode;

        /**
         * Pod logs.
         */
        private String logs = "";

        /**
         * Last runtime ref.
         */
        private DataxKubernetesRuntimeRef lastRuntimeRef;

        /**
         * Stop exception.
         */
        private RuntimeException stopException;

        @Override
        public DataxKubernetesRuntimeRef submit(DataxExecutionParam param) {
            submitCount++;
            return DataxKubernetesRuntimeRef.builder()
                    .namespace(param.getKubernetes().getNamespace())
                    .jobName(param.getKubernetes().getJobName())
                    .secretName(param.getKubernetes().getSecretName())
                    .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                    .containerName(param.getKubernetes().getContainerName())
                    .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                    .deleteJobOnFinish(param.getKubernetes().isDeleteJobOnFinish())
                    .build();
        }

        @Override
        public boolean cleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode) {
            cleanupCount++;
            lastRuntimeRef = runtimeRef;
            lastCleanupMode = mode;
            return cleanupResult;
        }

        @Override
        public void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
            if (stopException != null) {
                throw stopException;
            }
            this.lastRuntimeRef = runtimeRef;
            this.forcibly = forcibly;
        }

        @Override
        public StatusEnum queryStatus(DataxKubernetesRuntimeRef runtimeRef, StatusEnum localState) {
            this.lastRuntimeRef = runtimeRef;
            return status;
        }

        @Override
        public String collectLogs(DataxKubernetesRuntimeRef runtimeRef) {
            return logs;
        }

    }
}
