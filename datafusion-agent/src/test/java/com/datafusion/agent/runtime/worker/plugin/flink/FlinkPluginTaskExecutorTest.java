package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesRuntimeRef;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkOperatorStatus;
import com.datafusion.agent.runtime.worker.plugin.flink.k8s.K8sOperatorClient;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FlinkPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
class FlinkPluginTaskExecutorTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Fixed task work directory.
     */
    private static final String WORK_DIR = "/tmp/datafusion/flink/task-1";

    @Test
    void shouldValidateWithoutRewritingPluginParam() {
        FlinkPluginTaskExecutor executor = executor(new FakeOperatorClient());
        WorkerTaskExecutionSnap snapshot = snapshot();
        ObjectNode pluginParam = (ObjectNode) snapshot.getPluginParam();

        executor.validate(context(snapshot, state(StatusEnum.SUBMITTING)));

        assertSame(pluginParam, snapshot.getPluginParam());
        assertEquals(FlinkRunMode.K8S_OPERATOR.name(), snapshot.getRunMode());
    }

    @Test
    void shouldWriteSubmitResultToCandidateState() {
        FakeOperatorClient client = new FakeOperatorClient();
        FlinkPluginTaskExecutor executor = executor(client);
        WorkerTaskExecutionState state = state(StatusEnum.SUBMITTING);

        WorkerResult result = executor.submit(context(snapshot(), state));

        assertEquals(StatusEnum.SUBMIT_SUCCESS, state.getStatus());
        assertEquals("df-flink-task-1", state.getAppId());
        assertEquals(WORK_DIR, state.getWorkDirPath());
        assertEquals("df-flink-task-1", result.getAppId());
        assertTrue(result.getPluginLogUri().contains("df-flink-task-1"));
    }

    @Test
    void shouldUseSnapshotAndCandidateStateForControlActions() {
        FakeOperatorClient client = new FakeOperatorClient();
        FlinkPluginTaskExecutor executor = executor(client);

        WorkerTaskExecutionState stopState = state(StatusEnum.STOPPING);
        WorkerResult stopResult = executor.stop(context(snapshot(), stopState));
        WorkerTaskExecutionState killState = state(StatusEnum.KILLING);
        WorkerResult killResult = executor.kill(context(snapshot(), killState));
        boolean finished = executor.finish(context(snapshot(), state(StatusEnum.RUN_SUCCESS)));

        assertEquals(StatusEnum.STOPPING, stopState.getStatus());
        assertEquals("df-flink-task-1", stopResult.getAppId());
        assertEquals("df-flink-task-1", client.stoppedRuntimeRef.getDeploymentName());
        assertEquals(StatusEnum.KILLING, killState.getStatus());
        assertEquals("df-flink-task-1", killResult.getAppId());
        assertEquals("df-flink-task-1", client.killedRuntimeRef.getDeploymentName());
        assertTrue(finished);
        assertEquals("df-flink-task-1", client.cleanedRuntimeRef.getDeploymentName());
    }

    private FlinkPluginTaskExecutor executor(K8sOperatorClient client) {
        return new FlinkPluginTaskExecutor(new FlinkParamResolver(), client);
    }

    private RunningTaskContext context(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return new RunningTaskContext(snapshot, state, WORK_DIR);
    }

    private WorkerTaskExecutionSnap snapshot() {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("Flink")
                .pluginType(FlinkPluginTaskExecutor.PLUGIN_TYPE)
                .runMode(FlinkRunMode.K8S_OPERATOR.name())
                .pluginParam(pluginParam())
                .taskData(taskData())
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .workerId("worker-1")
                .appId(status == StatusEnum.SUBMITTING ? null : "df-flink-task-1")
                .workDirPath(WORK_DIR)
                .status(status)
                .build();
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
     * 测试用 Operator 客户端.
     */
    private static class FakeOperatorClient implements K8sOperatorClient {

        /**
         * 已停止运行引用.
         */
        private FlinkKubernetesRuntimeRef stoppedRuntimeRef;

        /**
         * 已清理运行引用.
         */
        private FlinkKubernetesRuntimeRef cleanedRuntimeRef;

        /**
         * 已强杀运行引用.
         */
        private FlinkKubernetesRuntimeRef killedRuntimeRef;

        @Override
        public FlinkKubernetesRuntimeRef submit(FlinkExecutionParam param) {
            return FlinkKubernetesRuntimeRef.builder()
                    .namespace(param.getKubernetes().getNamespace())
                    .deploymentName(param.getKubernetes().getDeploymentName())
                    .flinkWebUiUri(param.getKubernetes().getFlinkWebUiUri())
                    .build();
        }

        @Override
        public void stop(FlinkKubernetesRuntimeRef runtimeRef) {
            stoppedRuntimeRef = runtimeRef;
        }

        @Override
        public void kill(FlinkKubernetesRuntimeRef runtimeRef) {
            killedRuntimeRef = runtimeRef;
        }

        @Override
        public FlinkOperatorStatus queryStatus(FlinkKubernetesRuntimeRef runtimeRef) {
            return null;
        }

        @Override
        public boolean runtimePodsExist(FlinkKubernetesRuntimeRef runtimeRef) {
            return false;
        }

        @Override
        public String collectLogs(FlinkKubernetesRuntimeRef runtimeRef) {
            return "";
        }

        @Override
        public boolean cleanup(FlinkKubernetesRuntimeRef runtimeRef) {
            cleanedRuntimeRef = runtimeRef;
            return true;
        }
    }
}
