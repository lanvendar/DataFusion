package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K8S_OPERATOR Flink 状态映射测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
class K8sOperatorRunModeStateMappingTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldExposeLowerCaseOperatorState() {
        assertEquals("running", FlinkOperatorStatus.State.RUNNING.getValue());
        assertEquals("suspended", FlinkOperatorStatus.State.SUSPENDED.getValue());
    }

    @Test
    void shouldMapOperatorStateByTaskState() {
        FakeOperatorClient client = new FakeOperatorClient();
        K8sOperatorRunModeStateMapping mapping = mapping(client);
        WorkerTaskExecutionSnap snapshot = snapshot();

        client.status = status(FlinkOperatorStatus.State.RECONCILING);
        assertEquals(StatusEnum.SUBMIT_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));
        assertEquals(StatusEnum.RUNNING, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.FAILING);
        assertEquals(StatusEnum.RUNNING, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.FINISHED);
        assertEquals(StatusEnum.RUN_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));
        assertEquals(StatusEnum.RUN_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.FAILED);
        assertEquals(StatusEnum.RUN_FAILURE, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));
        assertEquals(StatusEnum.RUN_FAILURE, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.CANCELLING);
        assertEquals(StatusEnum.RUNNING, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));
        assertEquals(StatusEnum.RUNNING, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.CANCELED);
        assertEquals(StatusEnum.RUN_FAILURE, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));
        assertEquals(StatusEnum.RUN_FAILURE, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = stoppedStatus(FlinkOperatorStatus.State.CANCELED, 1L, 1L);
        assertEquals(StatusEnum.STOP_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = stoppedStatus(FlinkOperatorStatus.State.FINISHED, 1L, 1L);
        assertEquals(StatusEnum.STOP_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = stoppedStatus(FlinkOperatorStatus.State.SUSPENDED, 1L, 1L);
        assertEquals(StatusEnum.STOP_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = stoppedStatus(FlinkOperatorStatus.State.FAILED, 1L, 1L);
        assertEquals(StatusEnum.STOP_FAILURE, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = missingStatus();
        client.runtimePodsExist = false;
        assertEquals(StatusEnum.KILLED, mapping.mapState(snapshot, state(StatusEnum.KILLING)));

        client.runtimePodsExist = true;
        assertEquals(StatusEnum.KILLING, mapping.mapState(snapshot, state(StatusEnum.KILLING)));
        assertEquals(StatusEnum.UNKNOWN, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));
    }

    @Test
    void shouldWaitForCurrentGenerationAndUseJobManagerErrorAsFallback() {
        FakeOperatorClient client = new FakeOperatorClient();
        K8sOperatorRunModeStateMapping mapping = mapping(client);
        WorkerTaskExecutionSnap snapshot = snapshot();

        client.status = stoppedStatus(FlinkOperatorStatus.State.SUSPENDED, 2L, 1L);
        assertEquals(StatusEnum.STOPPING, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = status(FlinkOperatorStatus.State.FINISHED, FlinkOperatorStatus.State.RUNNING,
                FlinkOperatorStatus.JobManagerState.READY, true, 2L, 1L);
        assertEquals(StatusEnum.RUNNING, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.RECONCILING, FlinkOperatorStatus.State.RUNNING,
                FlinkOperatorStatus.JobManagerState.ERROR, true, 2L, 2L);
        assertEquals(StatusEnum.SUBMIT_FAILURE, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));
        assertEquals(StatusEnum.RUN_FAILURE, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.UNKNOWN, FlinkOperatorStatus.State.RUNNING,
                FlinkOperatorStatus.JobManagerState.ERROR, true, 2L, 2L);
        assertEquals(StatusEnum.RUN_FAILURE, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.UNKNOWN, FlinkOperatorStatus.State.SUSPENDED,
                FlinkOperatorStatus.JobManagerState.ERROR, true, 2L, 2L);
        assertEquals(StatusEnum.STOP_FAILURE, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));
    }

    @Test
    void shouldUseRuntimePodsOnlyWhenOperatorResourceIsMissing() {
        FakeOperatorClient client = new FakeOperatorClient();
        K8sOperatorRunModeStateMapping mapping = mapping(client);
        WorkerTaskExecutionSnap snapshot = snapshot();
        client.status = missingStatus();

        client.runtimePodsExist = true;
        assertEquals(StatusEnum.STOPPING, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.runtimePodsExist = false;
        assertEquals(StatusEnum.STOP_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));
    }

    @Test
    void shouldPrepareFinalResultOnce() {
        K8sOperatorRunModeStateMapping mapping = mapping(new FakeOperatorClient());
        WorkerTaskExecutionState state = state(StatusEnum.RUN_SUCCESS);

        assertTrue(mapping.prepareFinalReport(snapshot(), state));
        assertTrue(state.getResult().path("finalized").asBoolean());
        assertFalse(mapping.prepareFinalReport(snapshot(), state));
    }

    @Test
    void shouldReturnStopFailureWithOperatorError() {
        FakeOperatorClient client = new FakeOperatorClient();
        client.stopFailure = new IllegalArgumentException("invalid desired state");
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveState(state(StatusEnum.RUNNING));
        FlinkPluginTaskExecutor executor = new FlinkPluginTaskExecutor(
                new FlinkParamResolver(new AgentProperties()), stateStore, client);

        TaskResult result = executor.stopTask(request());

        assertEquals(StatusEnum.STOP_FAILURE, result.getTaskState());
        assertEquals("df-flink-task-1", result.getWorkerResult().getAppId());
        assertTrue(result.getWorkerResult().getMessage().contains("invalid desired state"));
    }

    private K8sOperatorRunModeStateMapping mapping(FakeOperatorClient client) {
        return new K8sOperatorRunModeStateMapping(client, new FlinkParamResolver(new AgentProperties()));
    }

    private WorkerTaskExecutionSnap snapshot() {
        TaskRequest request = request();
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(FlinkRunMode.K8S_OPERATOR.name())
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-flink-task-1")
                .status(status)
                .build();
    }

    private FlinkOperatorStatus status(FlinkOperatorStatus.State state) {
        return status(state, FlinkOperatorStatus.State.RUNNING, FlinkOperatorStatus.JobManagerState.READY,
                true, 1L, 1L);
    }

    private FlinkOperatorStatus status(FlinkOperatorStatus.State state, FlinkOperatorStatus.State desiredState,
            FlinkOperatorStatus.JobManagerState jobManagerState, boolean deploymentExists, Long generation,
            Long observedGeneration) {
        return FlinkOperatorStatus.builder()
                .state(state)
                .desiredState(desiredState)
                .jobManagerState(jobManagerState)
                .deploymentExists(deploymentExists)
                .generation(generation)
                .observedGeneration(observedGeneration)
                .build();
    }

    private FlinkOperatorStatus stoppedStatus(FlinkOperatorStatus.State state, Long generation,
            Long observedGeneration) {
        return status(state, FlinkOperatorStatus.State.SUSPENDED, FlinkOperatorStatus.JobManagerState.READY,
                true, generation, observedGeneration);
    }

    private FlinkOperatorStatus missingStatus() {
        return status(FlinkOperatorStatus.State.NONE, FlinkOperatorStatus.State.NONE,
                FlinkOperatorStatus.JobManagerState.NONE, false, null, null);
    }

    private TaskRequest request() {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("Flink");
        request.setPluginType(FlinkPluginTaskExecutor.PLUGIN_TYPE);
        request.setRunMode(FlinkRunMode.K8S_OPERATOR.name());
        request.setPluginParam(pluginParam());
        request.setTaskData(taskData());
        return request;
    }

    private ObjectNode pluginParam() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("flinkAppDir", "/opt/datafusion/plugins/flink/datafusion-plugin-flink-table");
        pluginParam.put("launchMode", "JAR");
        pluginParam.put("flinkAppJar", "datafusion-plugin-flink-table-1.0.0-executable.jar");
        pluginParam.put("mainClass", "com.datafusion.plugin.flink.table.FlinkTablePaimonApplication");
        pluginParam.put("flinkVersion", "2.2.0");
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("image", "flink:2.2.0-scala_2.12-java17");
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
        kubernetes.put("collectLogsOnFinish", false);
        pluginParam.set("kubernetes", kubernetes);
        return pluginParam;
    }

    private ObjectNode taskData() {
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        ObjectNode job = OBJECT_MAPPER.createObjectNode();
        job.put("id", "flink-job");
        taskData.set("job", job);
        return taskData;
    }

    /**
     * 测试用 Operator client.
     */
    private static class FakeOperatorClient implements K8sOperatorClient {

        /**
         * Operator 状态.
         */
        private FlinkOperatorStatus status;

        /**
         * 停止异常.
         */
        private RuntimeException stopFailure;

        /**
         * 是否存在运行 Pod.
         */
        private boolean runtimePodsExist;

        @Override
        public FlinkKubernetesRuntimeRef submit(FlinkExecutionParam param) {
            return null;
        }

        @Override
        public void stop(FlinkKubernetesRuntimeRef runtimeRef) {
            if (stopFailure != null) {
                throw stopFailure;
            }
        }

        @Override
        public void kill(FlinkKubernetesRuntimeRef runtimeRef) {

        }

        @Override
        public FlinkOperatorStatus queryStatus(FlinkKubernetesRuntimeRef runtimeRef) {
            return status;
        }

        @Override
        public boolean runtimePodsExist(FlinkKubernetesRuntimeRef runtimeRef) {
            return runtimePodsExist;
        }

        @Override
        public String collectLogs(FlinkKubernetesRuntimeRef runtimeRef) {
            return "";
        }

        @Override
        public boolean cleanup(FlinkKubernetesRuntimeRef runtimeRef) {
            return true;
        }
    }
}
