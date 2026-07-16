package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkRunMode;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkTaskResult;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldMapOperatorStateByLocalStage() {
        FakeOperatorClient client = new FakeOperatorClient();
        K8sOperatorRunModeStateMapping mapping = mapping(client);

        client.status = status(FlinkOperatorStatus.State.RECONCILING, true, true, true);
        assertEquals(StatusEnum.SUBMIT_SUCCESS, mapping.mapState(state(StatusEnum.SUBMIT_SUCCESS)));

        client.status = status(FlinkOperatorStatus.State.FAILING, true, true, true);
        assertEquals(StatusEnum.RUNNING, mapping.mapState(state(StatusEnum.RUNNING)));

        client.status = status(FlinkOperatorStatus.State.CANCELED, true, true, true);
        assertEquals(StatusEnum.STOP_SUCCESS, mapping.mapState(state(StatusEnum.STOPPING)));

        client.status = status(FlinkOperatorStatus.State.NONE, false, false, false);
        assertEquals(StatusEnum.KILLED, mapping.mapState(state(StatusEnum.KILLING)));

        client.status = status(FlinkOperatorStatus.State.NONE, false, true, false);
        assertEquals(StatusEnum.UNKNOWN, mapping.mapState(state(StatusEnum.RUNNING)));
    }

    @Test
    void shouldReturnStopFailureWithOperatorError() {
        FakeOperatorClient client = new FakeOperatorClient();
        client.stopFailure = new IllegalArgumentException("invalid desired state");
        FlinkExecutionParam param = new FlinkParamResolver(new AgentProperties()).resolve(request());

        FlinkTaskResult result = new K8sOperatorFlinkTaskRunner(client).stop(param, state(StatusEnum.RUNNING));

        assertEquals(StatusEnum.STOP_FAILURE, result.getStatus());
        assertEquals("df-flink-task-1", result.getAppId());
        assertTrue(result.getResult().path("message").asText().contains("invalid desired state"));
    }

    private K8sOperatorRunModeStateMapping mapping(FakeOperatorClient client) {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        TaskRequest request = request();
        stateStore.saveSnapshot(WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(FlinkRunMode.K8S_OPERATOR.name())
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build());
        return new K8sOperatorRunModeStateMapping(client, new FlinkParamResolver(new AgentProperties()), stateStore);
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-flink-task-1")
                .status(status)
                .build();
    }

    private FlinkOperatorStatus status(FlinkOperatorStatus.State state, boolean deploymentExists,
            boolean podExists, boolean serviceExists) {
        return FlinkOperatorStatus.builder()
                .state(state)
                .deploymentExists(deploymentExists)
                .podExists(podExists)
                .serviceExists(serviceExists)
                .build();
    }

    private TaskRequest request() {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("Flink");
        request.setPluginType(FlinkPluginTaskExecutor.PLUGIN_TYPE);
        request.setPluginParam(pluginParam());
        request.setTaskData(taskData());
        return request;
    }

    private ObjectNode pluginParam() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put(FlinkParamResolver.FIELD_RUN_MODE, FlinkRunMode.K8S_OPERATOR.name());
        pluginParam.put("flinkAppDir", "/opt/datafusion/plugins/flink/datafusion-plugin-flink-table");
        pluginParam.put("launchMode", "JAR");
        pluginParam.put("flinkAppJar", "datafusion-plugin-flink-table-1.0.0-executable.jar");
        pluginParam.put("mainClass", "com.datafusion.plugin.flink.table.FlinkTablePaimonApplication");
        pluginParam.put("flinkVersion", "2.2.0");
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("image", "flink:2.2.0-scala_2.12-java17");
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
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
        public String collectLogs(FlinkKubernetesRuntimeRef runtimeRef) {
            return "";
        }

        @Override
        public boolean cleanup(FlinkKubernetesRuntimeRef runtimeRef) {
            return true;
        }
    }
}
