package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setModules(tempDir.toString());
        return properties;
    }

    private K8sDataxTaskRunner runner(FakeKubernetesClient client) {
        AgentProperties properties = properties();
        return new K8sDataxTaskRunner(client, properties, new DataxParamResolver(properties));
    }

    private TaskRequest request() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode pluginKubernetes = OBJECT_MAPPER.createObjectNode();
        pluginKubernetes.put("namespace", "df");
        pluginKubernetes.put("image", "datafusion/datax:latest");
        pluginKubernetes.put("collectLogsOnFinish", false);
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

        @Override
        public DataxKubernetesRuntimeRef submit(DataxExecutionParam param) {
            return null;
        }

        @Override
        public void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly) {
            this.forcibly = forcibly;
        }

        @Override
        public StatusEnum queryStatus(DataxKubernetesRuntimeRef runtimeRef, StatusEnum localState) {
            return status;
        }

        @Override
        public String collectLogs(DataxKubernetesRuntimeRef runtimeRef) {
            return "";
        }

        @Override
        public void cleanup(DataxKubernetesRuntimeRef runtimeRef) {
            cleanupCount++;
        }
    }
}
