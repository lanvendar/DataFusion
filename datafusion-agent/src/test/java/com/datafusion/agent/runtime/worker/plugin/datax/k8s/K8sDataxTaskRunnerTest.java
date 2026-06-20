package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        TaskResult result = runner.stop(request(), state(StatusEnum.RUNNING));

        assertEquals(StatusEnum.STOPPING, result.getTaskState());
        assertEquals(false, client.forcibly);
    }

    @Test
    void shouldReturnFinalStopStateOnlyAfterStatusMappingBecomesFinal() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sDataxTaskRunner runner = runner(client);

        client.status = StatusEnum.STOPPING;
        TaskResult stopping = runner.finish(request(), state(StatusEnum.STOPPING));
        client.status = StatusEnum.STOP_SUCCESS;
        TaskResult stopped = runner.finish(request(), state(StatusEnum.STOPPING));

        assertEquals(StatusEnum.STOPPING, stopping.getTaskState());
        assertEquals(StatusEnum.STOP_SUCCESS, stopped.getTaskState());
        assertEquals(1, client.cleanupCount);
    }

    @Test
    void shouldCollectKubernetesLogsToTaskRuntimeDir() throws Exception {
        FakeKubernetesClient client = new FakeKubernetesClient();
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        K8sDataxTaskRunner runner = runner(client, stateStore);
        client.status = StatusEnum.RUN_SUCCESS;
        client.logs = "k8s datax logs";

        TaskRequest request = request(true);
        stateStore.saveSnapshot(snapshot(request));
        TaskResult result = runner.finish(minimalRequest(), state(StatusEnum.RUNNING));

        Path logFile = Path.of(result.getLogPath());
        assertEquals("k8s-datax.log", logFile.getFileName().toString());
        assertTrue(result.getLogPath().contains("task-runtime"));
        assertEquals("k8s datax logs", Files.readString(logFile));
        assertEquals(result.getLogPath(), result.getResult().path("pluginLogUri").asText());
    }

    @Test
    void shouldRestoreRuntimeRefFromSnapshotWhenControlRequestIsMinimal() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        K8sDataxTaskRunner runner = runner(client, stateStore);
        TaskRequest request = request(false);
        stateStore.saveSnapshot(snapshot(request));

        TaskResult result = runner.stop(minimalRequest(), state(StatusEnum.RUNNING));

        assertEquals(StatusEnum.STOPPING, result.getTaskState());
        assertEquals("flow-1", result.getFlowInstanceId());
        assertEquals("DataX", result.getTaskName());
        assertEquals("df-datax-task-1", client.lastRuntimeRef.getJobName());
        assertEquals("df", client.lastRuntimeRef.getNamespace());
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setModules(tempDir.toString());
        return properties;
    }

    private K8sDataxTaskRunner runner(FakeKubernetesClient client) {
        return runner(client, new InMemoryWorkerTaskExecutionStore());
    }

    private K8sDataxTaskRunner runner(FakeKubernetesClient client, InMemoryWorkerTaskExecutionStore stateStore) {
        AgentProperties properties = properties();
        return new K8sDataxTaskRunner(client, properties, new DataxParamResolver(properties), stateStore);
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

    private TaskRequest minimalRequest() {
        TaskRequest request = new TaskRequest();
        request.setTaskInstanceId("task-1");
        return request;
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode("K8S")
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-datax-task-1")
                .status(status)
                .build();
    }

    private DataxKubernetesRuntimeRef runtimeRef() {
        return DataxKubernetesRuntimeRef.builder()
                .namespace("df")
                .jobName("df-datax-task-1")
                .secretName("df-datax-job-task-1")
                .podLabelSelector("datafusion.io/task-instance-id=task-1")
                .containerName("datax")
                .collectLogsOnFinish(false)
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
         * Cleanup count.
         */
        private int cleanupCount;

        /**
         * Pod logs.
         */
        private String logs = "";

        /**
         * Last runtime ref.
         */
        private DataxKubernetesRuntimeRef lastRuntimeRef;

        @Override
        public DataxKubernetesRuntimeRef submit(DataxExecutionParam param) {
            return null;
        }

        @Override
        public void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
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

        @Override
        public void cleanup(DataxKubernetesRuntimeRef runtimeRef) {
            cleanupCount++;
        }
    }
}
