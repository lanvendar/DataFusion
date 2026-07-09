package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void shouldValidateWithoutRewritingPluginParam() {
        FlinkPluginTaskExecutor executor = executor(new InMemoryWorkerTaskExecutionStore(), new FakeFlinkRunner());
        TaskRequest request = request();
        ObjectNode pluginParam = (ObjectNode) request.getPluginParam();

        executor.validateTaskRequest(request);

        assertSame(pluginParam, request.getPluginParam());
        assertEquals(FlinkRunMode.K8S_OPERATOR.name(), request.getPluginParam().path(FlinkParamResolver.FIELD_RUN_MODE).asText());
    }

    @Test
    void shouldResolveRunModeFromSnapshotWhenPluginParamMissingRunMode() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        FakeFlinkRunner runner = new FakeFlinkRunner();
        FlinkPluginTaskExecutor executor = executor(stateStore, runner);
        TaskRequest sourceRequest = request();
        ObjectNode pluginParam = sourceRequest.getPluginParam().deepCopy();
        pluginParam.remove(FlinkParamResolver.FIELD_RUN_MODE);
        stateStore.saveSnapshot(WorkerTaskExecutionSnap.builder()
                .flowInstanceId(sourceRequest.getFlowInstanceId())
                .taskInstanceId(sourceRequest.getTaskInstanceId())
                .taskName(sourceRequest.getTaskName())
                .pluginType(sourceRequest.getPluginType())
                .runMode(FlinkRunMode.K8S_OPERATOR.name())
                .taskData(sourceRequest.getTaskData())
                .pluginParam(pluginParam)
                .build());
        stateStore.saveState(WorkerTaskExecutionState.builder()
                .taskInstanceId(sourceRequest.getTaskInstanceId())
                .status(StatusEnum.RUNNING)
                .appId("df-flink-task-1")
                .build());

        TaskRequest controlRequest = new TaskRequest();
        controlRequest.setTaskInstanceId(sourceRequest.getTaskInstanceId());
        TaskResult stopResult = executor.stopTask(controlRequest);
        boolean finished = executor.finishTask(controlRequest);

        assertEquals(StatusEnum.STOPPING, stopResult.getTaskState());
        assertEquals(FlinkRunMode.K8S_OPERATOR, runner.lastStopRunMode);
        assertTrue(finished);
        assertEquals(FlinkRunMode.K8S_OPERATOR, runner.lastFinishRunMode);
    }

    private FlinkPluginTaskExecutor executor(InMemoryWorkerTaskExecutionStore stateStore, FakeFlinkRunner runner) {
        return new FlinkPluginTaskExecutor(new FlinkParamResolver(new AgentProperties()), stateStore, List.of(runner));
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
     * Fake Flink runner.
     */
    private static class FakeFlinkRunner implements FlinkTaskRunner {

        /**
         * Last stop run mode.
         */
        private FlinkRunMode lastStopRunMode;

        /**
         * Last finish run mode.
         */
        private FlinkRunMode lastFinishRunMode;

        @Override
        public FlinkRunMode runMode() {
            return FlinkRunMode.K8S_OPERATOR;
        }

        @Override
        public FlinkTaskResult submit(FlinkExecutionParam param) {
            return FlinkTaskResult.builder()
                    .status(StatusEnum.SUBMIT_SUCCESS)
                    .appId(param.getKubernetes().getDeploymentName())
                    .workDirPath(param.getWorkDir().toString())
                    .build();
        }

        @Override
        public FlinkTaskResult stop(FlinkExecutionParam param, WorkerTaskExecutionState state) {
            lastStopRunMode = param.getRunMode();
            return FlinkTaskResult.builder().status(StatusEnum.STOPPING).build();
        }

        @Override
        public FlinkTaskResult kill(FlinkExecutionParam param, WorkerTaskExecutionState state) {
            return FlinkTaskResult.builder().status(StatusEnum.KILLING).build();
        }

        @Override
        public boolean finish(FlinkExecutionParam param, WorkerTaskExecutionState state) {
            lastFinishRunMode = param.getRunMode();
            return true;
        }
    }
}
