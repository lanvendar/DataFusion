package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.agent.runtime.worker.plugin.spark.k8s.K8sOperatorClient;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesRuntimeRef;
import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkOperatorStatus;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spark 插件任务执行器测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class SparkPluginTaskExecutorTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 当前任务工作目录.
     */
    private static final String CURRENT_WORK_DIR = "/tmp/datafusion/spark/current";

    @Test
    void shouldCleanupDeterministicRuntimeBeforeSubmit() {
        FakeOperatorClient client = new FakeOperatorClient();
        SparkPluginTaskExecutor executor = new SparkPluginTaskExecutor(new SparkParamResolver(), client);
        WorkerTaskExecutionState candidate = candidateState();
        RunningTaskContext context = new RunningTaskContext(snapshot("new-ns"), candidate, CURRENT_WORK_DIR);

        WorkerResult result = executor.submit(context);

        assertTrue(client.submitRequested);
        assertEquals("new-ns", client.cleanedRuntimeRef.getNamespace());
        assertEquals("df-spark-task-1", client.cleanedRuntimeRef.getApplicationName());
        assertEquals(StatusEnum.SUBMIT_SUCCESS, candidate.getStatus());
        assertEquals("df-spark-task-1", candidate.getAppId());
        assertEquals(CURRENT_WORK_DIR, candidate.getWorkDirPath());
        assertEquals(candidate.getAppId(), result.getAppId());
    }

    @Test
    void shouldRejectSubmitWhenCleanupFails() {
        FakeOperatorClient client = new FakeOperatorClient();
        client.cleanupSuccess = false;
        SparkPluginTaskExecutor executor = new SparkPluginTaskExecutor(new SparkParamResolver(), client);
        WorkerTaskExecutionState candidate = candidateState();
        RunningTaskContext context = new RunningTaskContext(snapshot("new-ns"), candidate, CURRENT_WORK_DIR);

        WorkerResult result = executor.submit(context);

        assertFalse(client.submitRequested);
        assertEquals(StatusEnum.SUBMIT_FAILURE, candidate.getStatus());
        assertEquals(CURRENT_WORK_DIR, result.getWorkDirPath());
        assertTrue(result.getMessage().contains("cleanup before submit failed"));
    }

    private WorkerTaskExecutionSnap snapshot(String namespace) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("Spark")
                .pluginType(SparkPluginTaskExecutor.PLUGIN_TYPE)
                .runMode(SparkRunMode.K8S_OPERATOR.name())
                .pluginParam(pluginParam(namespace))
                .taskData(OBJECT_MAPPER.createObjectNode())
                .build();
    }

    private ObjectNode pluginParam(String namespace) {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        ObjectNode kubernetes = pluginParam.putObject("kubernetes");
        kubernetes.put("namespace", namespace);
        kubernetes.put("serviceAccountName", "spark-driver");
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
        kubernetes.put("pluginAppDir", "/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql");
        return pluginParam;
    }

    private WorkerTaskExecutionState candidateState() {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .workerId("worker-1")
                .workDirPath(CURRENT_WORK_DIR)
                .status(StatusEnum.SUBMITTING)
                .build();
    }

    /**
     * 测试用 Operator 客户端.
     */
    private static class FakeOperatorClient implements K8sOperatorClient {

        /**
         * 清理是否成功.
         */
        private boolean cleanupSuccess = true;

        /**
         * 是否请求提交.
         */
        private boolean submitRequested;

        /**
         * 已清理运行引用.
         */
        private SparkKubernetesRuntimeRef cleanedRuntimeRef;

        @Override
        public SparkKubernetesRuntimeRef submit(SparkExecutionParam param) {
            submitRequested = true;
            return SparkKubernetesRuntimeRef.builder()
                    .namespace(param.getKubernetes().getNamespace())
                    .applicationName(param.getKubernetes().getApplicationName())
                    .configMapName(param.getKubernetes().getConfigMapName())
                    .sparkWebUiUri(param.getKubernetes().getSparkWebUiUri())
                    .build();
        }

        @Override
        public void stop(SparkKubernetesRuntimeRef runtimeRef) {
        }

        @Override
        public void kill(SparkKubernetesRuntimeRef runtimeRef) {
        }

        @Override
        public SparkOperatorStatus queryStatus(SparkKubernetesRuntimeRef runtimeRef) {
            return null;
        }

        @Override
        public String collectLogs(SparkKubernetesRuntimeRef runtimeRef) {
            return "";
        }

        @Override
        public boolean cleanup(SparkKubernetesRuntimeRef runtimeRef) {
            cleanedRuntimeRef = runtimeRef;
            return cleanupSuccess;
        }
    }
}
