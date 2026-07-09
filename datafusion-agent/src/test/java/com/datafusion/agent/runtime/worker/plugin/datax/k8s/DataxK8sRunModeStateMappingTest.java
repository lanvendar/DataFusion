package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DataxK8sRunModeStateMapping}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/24
 * @since 1.0.0
 */
class DataxK8sRunModeStateMappingTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void shouldCollectLogsBeforeFinalStateReport() throws Exception {
        FakeKubernetesClient client = new FakeKubernetesClient();
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        AgentProperties properties = properties();
        DataxK8sRunModeStateMapping mapping = new DataxK8sRunModeStateMapping(client,
                new DataxParamResolver(properties), stateStore);
        TaskRequest request = request();
        WorkerTaskExecutionState state = state();
        stateStore.saveSnapshot(snapshot(request));

        mapping.beforeFinalReport(state);

        Path logFile = tempDir.resolve("task-runtime").resolve("20260624").resolve("flow-1")
                .resolve("task-1").resolve("k8s-datax.log");
        assertEquals("k8s datax logs", Files.readString(logFile));
        assertEquals(logFile.toString(), state.getResult().path("pluginLogUri").asText());
        assertTrue(state.getResult().path("finalized").asBoolean());
        assertEquals(0, client.cleanupCount);
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.getStorage().setTaskRuntimeDir(tempDir.resolve("task-runtime").toString());
        return properties;
    }

    private TaskRequest request() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("namespace", "df");
        kubernetes.put("image", "datafusion/datax:latest");
        kubernetes.put("collectLogsOnFinish", true);
        pluginParam.set("kubernetes", kubernetes);
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.set("jobJson", OBJECT_MAPPER.createObjectNode());
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("DataX");
        request.setPluginType(DataxPluginTaskExecutor.PLUGIN_TYPE);
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);
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

    private WorkerTaskExecutionState state() {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-datax-task-1")
                .workDirPath(tempDir.resolve("task-runtime").resolve("20260624")
                        .resolve("flow-1").resolve("task-1").toString())
                .status(StatusEnum.RUN_SUCCESS)
                .build();
    }

    /**
     * Fake Kubernetes client.
     */
    private static class FakeKubernetesClient implements DataxKubernetesClient {

        /**
         * Cleanup count.
         */
        private int cleanupCount;

        /**
         * Last cleanup mode.
         */
        private DataxKubernetesCleanupMode lastCleanupMode;

        @Override
        public DataxKubernetesRuntimeRef submit(DataxExecutionParam param) {
            return null;
        }

        @Override
        public boolean cleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode) {
            cleanupCount++;
            lastCleanupMode = mode;
            return true;
        }

        @Override
        public void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
        }

        @Override
        public StatusEnum queryStatus(DataxKubernetesRuntimeRef runtimeRef, StatusEnum localState) {
            return StatusEnum.RUN_SUCCESS;
        }

        @Override
        public String collectLogs(DataxKubernetesRuntimeRef runtimeRef) {
            return "k8s datax logs";
        }
    }
}
