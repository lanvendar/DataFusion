package com.datafusion.agent.runtime.worker.context;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStateStore;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AgentWorkerTaskContextStorage}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
class AgentWorkerTaskContextStorageTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldPreserveKubernetesRuntimeRefAndLogPathWhenSavingContext() {
        InMemoryWorkerTaskExecutionStateStore stateStore = new InMemoryWorkerTaskExecutionStateStore();
        stateStore.record(existingState());
        AgentWorkerTaskContextStorage storage = new AgentWorkerTaskContextStorage(stateStore);

        RunningTaskContext context = new RunningTaskContext();
        context.setFlowInstanceId("flow-1");
        context.setTaskInstanceId("task-1");
        context.setPluginType("DATAX");
        context.setRunMode("K8S");
        context.setTaskState(StatusEnum.RUNNING);
        context.setPluginParam(pluginParamWithoutRuntime());

        storage.save(context);

        WorkerTaskExecutionState saved = stateStore.read("task-1").orElseThrow();
        assertEquals("df-datax-task-1", saved.getPluginParam().path(DataxExecutionParam.RUNTIME_FIELD)
                .path("jobName").asText());
        assertEquals("k8s://df/jobs/df-datax-task-1", saved.getLogPath());
    }

    private WorkerTaskExecutionState existingState() {
        ObjectNode runtime = OBJECT_MAPPER.createObjectNode();
        runtime.put("namespace", "df");
        runtime.put("jobName", "df-datax-task-1");
        runtime.put("secretName", "df-datax-job-task-1");
        runtime.put("podLabelSelector", "datafusion.io/task-instance-id=task-1");
        runtime.put("containerName", "datax");
        runtime.put("collectLogsOnFinish", true);
        ObjectNode pluginParam = pluginParamWithoutRuntime();
        pluginParam.set(DataxExecutionParam.RUNTIME_FIELD, runtime);
        return WorkerTaskExecutionState.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .pluginType("DATAX")
                .runMode("K8S")
                .appId("df-datax-task-1")
                .logPath("k8s://df/jobs/df-datax-task-1")
                .status(StatusEnum.RUNNING)
                .pluginParam(pluginParam)
                .build();
    }

    private ObjectNode pluginParamWithoutRuntime() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        return pluginParam;
    }
}
