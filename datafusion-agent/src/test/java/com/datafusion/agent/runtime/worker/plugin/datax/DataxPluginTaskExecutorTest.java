package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

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
    void shouldPreserveServiceSnapshotAndPersistSubmitState() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        DataxPluginTaskExecutor executor = new FakeK8sExecutor(stateStore);
        TaskRequest request = request();
        stateStore.saveSnapshot(snapshot(request));
        stateStore.saveState(WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .status(StatusEnum.SUBMITTING)
                .build(), 0L);

        TaskResult result = executor.submitTask(request);

        WorkerTaskExecutionSnap snap = stateStore.readSnapshot("task-1").orElseThrow();
        final WorkerTaskExecutionState state = stateStore.readState("task-1").orElseThrow();
        assertEquals(StatusEnum.SUBMIT_SUCCESS, result.getTaskState());
        assertEquals("flow-1", snap.getFlowInstanceId());
        assertEquals(DataxPluginTaskExecutor.PLUGIN_TYPE, snap.getPluginType());
        assertEquals(DataxRunMode.K8S.name(), snap.getRunMode());
        assertEquals(SubmitModeEnum.ASYNC, snap.getSubmitMode());
        assertEquals(DataxRunMode.K8S.name(), executor.runMode());
        assertEquals(result.getWorkerResult().getAppId(), state.getAppId());
        assertEquals(result.getWorkerResult().getWorkDirPath(), state.getWorkDirPath());
        assertTrue(snap.getPluginParam().path("kubernetes").path("collectLogsOnFinish").asBoolean());
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(request.getRunMode())
                .submitMode(SubmitModeEnum.ASYNC)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    @Test
    void shouldResolveRunModeFromSnapshotWhenPluginParamMissingRunMode() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        final FakeK8sExecutor executor = new FakeK8sExecutor(stateStore);
        TaskRequest sourceRequest = request();
        ObjectNode pluginParam = sourceRequest.getPluginParam().deepCopy();
        stateStore.saveSnapshot(WorkerTaskExecutionSnap.builder()
                .flowInstanceId(sourceRequest.getFlowInstanceId())
                .taskInstanceId(sourceRequest.getTaskInstanceId())
                .taskName(sourceRequest.getTaskName())
                .pluginType(sourceRequest.getPluginType())
                .runMode(DataxRunMode.K8S.name())
                .taskData(sourceRequest.getTaskData())
                .pluginParam(pluginParam)
                .build());
        stateStore.saveState(WorkerTaskExecutionState.builder()
                .taskInstanceId(sourceRequest.getTaskInstanceId())
                .status(StatusEnum.RUNNING)
                .appId("df-datax-task-1")
                .build(), 0L);

        TaskRequest controlRequest = new TaskRequest();
        controlRequest.setTaskInstanceId(sourceRequest.getTaskInstanceId());
        TaskResult result = executor.stopTask(controlRequest);

        assertEquals(StatusEnum.STOPPING, result.getTaskState());
        assertEquals(DataxRunMode.K8S, executor.lastStopRunMode);
    }

    private TaskRequest request() {
        final ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
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
        request.setRunMode(DataxRunMode.K8S.name());
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);
        return request;
    }

    /**
     * Fake K8S executor.
     */
    private static class FakeK8sExecutor extends DataxPluginTaskExecutor {

        /**
         * Last stop run mode.
         */
        private DataxRunMode lastStopRunMode;

        FakeK8sExecutor(InMemoryWorkerTaskExecutionStore stateStore) {
            super(new DataxParamResolver(new AgentProperties()), stateStore);
        }

        @Override
        public String runMode() {
            return DataxRunMode.K8S.name();
        }

        @Override
        protected TaskResult submit(TaskRequest request, DataxExecutionParam param,
                WorkerTaskExecutionState state) {
            DataxTaskResult result = DataxTaskResult.builder()
                    .status(StatusEnum.SUBMIT_SUCCESS)
                    .appId(param.getKubernetes().getJobName())
                    .workDirPath(param.getWorkDir().toString())
                    .result(JacksonUtils.createObjectNode().put("message", "submitted"))
                    .build();
            return recordSubmitResult(request, state, result);
        }

        @Override
        protected DataxTaskResult stop(DataxExecutionParam param, WorkerTaskExecutionState state) {
            lastStopRunMode = param.getRunMode();
            return DataxTaskResult.builder().status(StatusEnum.STOPPING).build();
        }

        @Override
        protected DataxTaskResult kill(DataxExecutionParam param, WorkerTaskExecutionState state) {
            return DataxTaskResult.builder().status(StatusEnum.KILLING).build();
        }

    }
}
