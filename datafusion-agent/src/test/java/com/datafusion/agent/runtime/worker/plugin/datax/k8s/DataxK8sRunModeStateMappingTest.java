package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataX K8S 状态映射测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/24
 * @since 1.0.0
 */
class DataxK8sRunModeStateMappingTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 临时目录.
     */
    @TempDir
    private Path tempDir;

    @Test
    void shouldCollectLogsBeforeFinalStateReport() throws Exception {
        FakeKubernetesClient client = new FakeKubernetesClient();
        AgentProperties properties = properties();
        DataxK8sRunModeStateMapping mapping = new DataxK8sRunModeStateMapping(client,
                new DataxParamResolver(properties));
        TaskRequest request = request();
        WorkerTaskExecutionSnap snapshot = snapshot(request);
        WorkerTaskExecutionState state = state();

        assertTrue(mapping.prepareFinalReport(snapshot, state));

        Path logFile = tempDir.resolve("task-runtime").resolve("20260624").resolve("flow-1")
                .resolve("task-1").resolve("k8s-datax.log");
        assertEquals("k8s datax logs", Files.readString(logFile));
        assertEquals(logFile.toString(), state.getResult().path("pluginLogUri").asText());
        assertTrue(state.getResult().path("finalized").asBoolean());
        assertFalse(mapping.prepareFinalReport(snapshot, state));
        assertEquals(0, client.cleanupCount);
    }

    @Test
    void shouldMapKubernetesStatus() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        DataxK8sRunModeStateMapping mapping = new DataxK8sRunModeStateMapping(client,
                new DataxParamResolver(properties()));
        TaskRequest request = request();
        WorkerTaskExecutionSnap snapshot = snapshot(request);

        client.status = status(DataxKubernetesStatus.State.ACTIVE, true, true, true, true);
        assertEquals(StatusEnum.RUNNING, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(DataxKubernetesStatus.State.COMPLETE, true, true, false, false);
        assertEquals(StatusEnum.RUN_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(DataxKubernetesStatus.State.NONE, true, false, false, false);
        assertEquals(StatusEnum.UNKNOWN, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(DataxKubernetesStatus.State.NONE, false, false, false, false);
        assertEquals(StatusEnum.KILLED, mapping.mapState(snapshot, state(StatusEnum.KILLING)));
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.getStorage().setTaskRuntimeDir(tempDir.resolve("task-runtime").toString());
        return properties;
    }

    private TaskRequest request() {
        final ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
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
        request.setRunMode("K8S");
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
        return state(StatusEnum.RUN_SUCCESS);
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-datax-task-1")
                .workDirPath(tempDir.resolve("task-runtime").resolve("20260624")
                        .resolve("flow-1").resolve("task-1").toString())
                .status(status)
                .build();
    }

    private DataxKubernetesStatus status(DataxKubernetesStatus.State state, boolean jobExists,
            boolean jobStatusExists, boolean podExists, boolean podRunning) {
        return DataxKubernetesStatus.builder()
                .state(state)
                .jobExists(jobExists)
                .jobStatusExists(jobStatusExists)
                .podExists(podExists)
                .podRunning(podRunning)
                .build();
    }

    /**
     * 测试用 Kubernetes client.
     */
    private static class FakeKubernetesClient implements DataxKubernetesClient {

        /**
         * 清理次数.
         */
        private int cleanupCount;

        /**
         * 最后一次清理模式.
         */
        private DataxKubernetesCleanupMode lastCleanupMode;

        /**
         * Kubernetes 状态.
         */
        private DataxKubernetesStatus status = DataxKubernetesStatus.builder()
                .state(DataxKubernetesStatus.State.COMPLETE)
                .jobExists(true)
                .jobStatusExists(true)
                .build();

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
        public DataxKubernetesStatus queryStatus(DataxKubernetesRuntimeRef runtimeRef) {
            return status;
        }

        @Override
        public String collectLogs(DataxKubernetesRuntimeRef runtimeRef) {
            return "k8s datax logs";
        }
    }
}
