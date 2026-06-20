package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesRuntimeRef;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DataxPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
class DataxPluginTaskExecutorTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldPersistSnapshotAndStateFromSubmitResult() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        DataxPluginTaskExecutor executor = new DataxPluginTaskExecutor(new DataxParamResolver(new AgentProperties()),
                stateStore, List.of(new FakeK8sRunner()));

        TaskResult result = executor.submitTask(request());

        WorkerTaskExecutionSnap snap = stateStore.readSnapshot("task-1").orElseThrow();
        WorkerTaskExecutionState state = stateStore.readState("task-1").orElseThrow();
        assertEquals(StatusEnum.RUNNING, result.getTaskState());
        assertEquals("flow-1", snap.getFlowInstanceId());
        assertEquals(DataxPluginTaskExecutor.PLUGIN_TYPE, snap.getPluginType());
        assertEquals(DataxRunMode.K8S.name(), snap.getRunMode());
        assertEquals("K8S", snap.getPluginParam().path("runMode").asText());
        assertEquals(result.getAppId(), state.getAppId());
        assertEquals(result.getLogPath(), state.getLogPath());
        assertTrue(snap.getPluginParam().path("kubernetes").path("collectLogsOnFinish").asBoolean());
    }

    private TaskRequest request() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode pluginKubernetes = OBJECT_MAPPER.createObjectNode();
        pluginKubernetes.put("namespace", "df");
        pluginKubernetes.put("image", "datafusion/datax:latest");
        pluginKubernetes.put("collectLogsOnFinish", true);
        pluginParam.set("kubernetes", pluginKubernetes);
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

    /**
     * Fake K8S runner.
     */
    private static class FakeK8sRunner implements DataxTaskRunner {

        @Override
        public DataxRunMode runMode() {
            return DataxRunMode.K8S;
        }

        @Override
        public DataxSubmitResult submit(TaskRequest request, DataxExecutionParam param) {
            DataxKubernetesRuntimeRef runtimeRef = DataxKubernetesRuntimeRef.builder()
                    .namespace(param.getKubernetes().getNamespace())
                    .jobName(param.getKubernetes().getJobName())
                    .secretName(param.getKubernetes().getSecretName())
                    .podLabelSelector(param.getKubernetes().getPodLabelSelector())
                    .containerName(param.getKubernetes().getContainerName())
                    .logStorageUri(param.getKubernetes().getLogStorageUri())
                    .collectLogsOnFinish(param.getKubernetes().isCollectLogsOnFinish())
                    .deleteJobOnFinish(param.getKubernetes().isDeleteJobOnFinish())
                    .build();
            return DataxSubmitResult.builder()
                    .status(StatusEnum.RUNNING)
                    .appId(runtimeRef.getJobName())
                    .logPath(param.getWorkDir().toString())
                    .kubernetesRuntimeRef(runtimeRef)
                    .result(JacksonUtils.createObjectNode().put("message", "submitted"))
                    .build();
        }

        @Override
        public TaskResult stop(TaskRequest request, WorkerTaskExecutionState state) {
            return TaskResult.builder().taskState(StatusEnum.STOPPING).build();
        }

        @Override
        public TaskResult kill(TaskRequest request, WorkerTaskExecutionState state) {
            return TaskResult.builder().taskState(StatusEnum.KILLING).build();
        }

        @Override
        public TaskResult finish(TaskRequest request, WorkerTaskExecutionState state) {
            return TaskResult.builder().taskState(StatusEnum.RUNNING).build();
        }
    }
}
