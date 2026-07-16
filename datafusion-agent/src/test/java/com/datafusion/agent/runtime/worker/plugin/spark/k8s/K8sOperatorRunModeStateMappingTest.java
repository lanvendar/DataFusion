package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spark K8S_OPERATOR 状态映射测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
class K8sOperatorRunModeStateMappingTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldMapSparkOperatorStates() {
        FakeKubernetesClient client = new FakeKubernetesClient();
        K8sOperatorRunModeStateMapping mapping = new K8sOperatorRunModeStateMapping(client,
                new SparkParamResolver(new AgentProperties()));
        TaskRequest request = request();
        WorkerTaskExecutionSnap snapshot = snapshot(request);

        client.status = status(SparkOperatorStatus.State.SUBMISSION_FAILED, true, true, true);
        assertEquals(StatusEnum.SUBMIT_FAILURE, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));

        client.status = status(SparkOperatorStatus.State.SUSPENDING, true, true, true);
        assertEquals(StatusEnum.STOPPING, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = status(SparkOperatorStatus.State.SUSPENDED, true, false, false);
        assertEquals(StatusEnum.STOP_SUCCESS, mapping.mapState(snapshot, state(StatusEnum.STOPPING)));

        client.status = status(SparkOperatorStatus.State.SUSPENDED, true, false, false);
        assertEquals(StatusEnum.UNKNOWN, mapping.mapState(snapshot, state(StatusEnum.RUNNING)));

        client.status = status(SparkOperatorStatus.State.NONE, false, false, false);
        assertEquals(StatusEnum.UNKNOWN, mapping.mapState(snapshot, state(StatusEnum.SUBMIT_SUCCESS)));

        client.status = status(SparkOperatorStatus.State.NONE, false, false, false);
        assertEquals(StatusEnum.KILLED, mapping.mapState(snapshot, state(StatusEnum.KILLING)));
    }

    @Test
    void shouldPrepareFinalResultOnce() {
        K8sOperatorRunModeStateMapping mapping = new K8sOperatorRunModeStateMapping(new FakeKubernetesClient(),
                new SparkParamResolver(new AgentProperties()));
        WorkerTaskExecutionState state = state(StatusEnum.RUN_SUCCESS);

        assertTrue(mapping.prepareFinalReport(snapshot(request()), state));
        assertTrue(state.getResult().path("finalized").asBoolean());
        assertFalse(mapping.prepareFinalReport(snapshot(request()), state));
    }

    private TaskRequest request() {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("Spark");
        request.setPluginType(SparkPluginTaskExecutor.PLUGIN_TYPE);
        request.setRunMode(SparkRunMode.K8S_OPERATOR.name());
        request.setPluginParam(pluginParam());
        request.setTaskData(OBJECT_MAPPER.createObjectNode());
        return request;
    }

    private ObjectNode pluginParam() {
        final ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("namespace", "datafusion");
        kubernetes.put("serviceAccountName", "spark-driver");
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
        kubernetes.put("pluginAppDir", "/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql");
        kubernetes.put("collectLogsOnFinish", false);
        pluginParam.set("kubernetes", kubernetes);
        return pluginParam;
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(SparkRunMode.K8S_OPERATOR.name())
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("df-spark-task-1")
                .status(status)
                .build();
    }

    private SparkOperatorStatus status(SparkOperatorStatus.State state, boolean applicationExists,
            boolean podExists, boolean serviceExists) {
        return SparkOperatorStatus.builder()
                .state(state)
                .applicationExists(applicationExists)
                .podExists(podExists)
                .podRunning(podExists)
                .serviceExists(serviceExists)
                .build();
    }

    /**
     * 测试用 Kubernetes client.
     */
    private static class FakeKubernetesClient implements K8sOperatorClient {

        /**
         * Kubernetes 状态.
         */
        private SparkOperatorStatus status;

        @Override
        public SparkKubernetesRuntimeRef submit(SparkExecutionParam param) {
            return null;
        }

        @Override
        public void stop(SparkKubernetesRuntimeRef runtimeRef) {
        }

        @Override
        public void kill(SparkKubernetesRuntimeRef runtimeRef) {
        }

        @Override
        public SparkOperatorStatus queryStatus(SparkKubernetesRuntimeRef runtimeRef) {
            return status;
        }

        @Override
        public String collectLogs(SparkKubernetesRuntimeRef runtimeRef) {
            return "";
        }

        @Override
        public boolean cleanup(SparkKubernetesRuntimeRef runtimeRef) {
            return true;
        }
    }
}
