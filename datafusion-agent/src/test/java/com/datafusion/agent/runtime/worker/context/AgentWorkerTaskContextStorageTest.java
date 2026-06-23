package com.datafusion.agent.runtime.worker.context;

import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateReportScheduler;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.reporter.NoopTaskResultReporter;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

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
    void shouldPersistSnapshotAndKeepExistingRuntimeStateWhenSavingContext() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveState(existingState());
        AgentWorkerTaskContextStorage storage = new AgentWorkerTaskContextStorage(stateStore, reportScheduler(stateStore));

        RunningTaskContext context = new RunningTaskContext();
        context.setFlowInstanceId("flow-1");
        context.setTaskInstanceId("task-1");
        context.setTaskName("task-name");
        context.setPluginType("DATAX");
        context.setRunMode("K8S");
        context.setTaskState(StatusEnum.RUNNING);
        context.setTaskData(OBJECT_MAPPER.createObjectNode().put("jobName", "job.json"));
        context.setPluginParam(pluginParam());

        storage.save(context);

        WorkerTaskExecutionSnap snap = stateStore.readSnapshot("task-1").orElseThrow();
        WorkerTaskExecutionState state = stateStore.readState("task-1").orElseThrow();
        assertEquals("flow-1", snap.getFlowInstanceId());
        assertEquals("DATAX", snap.getPluginType());
        assertEquals("K8S", snap.getRunMode());
        assertEquals("app-1", state.getAppId());
        assertEquals("/opt/datafusion/task-runtime/20260622/flow-1/task-1", state.getWorkDirPath());
    }

    @Test
    void shouldRemoveStateWhenRemovingContext() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        stateStore.saveState(existingState());
        AgentWorkerTaskContextStorage storage = new AgentWorkerTaskContextStorage(stateStore, reportScheduler(stateStore));

        storage.remove("task-1");

        assertEquals(0, stateStore.listListeningStates().size());
    }

    private WorkerTaskExecutionState existingState() {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("app-1")
                .workDirPath("/opt/datafusion/task-runtime/20260622/flow-1/task-1")
                .status(StatusEnum.RUNNING)
                .build();
    }

    private ObjectNode pluginParam() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        return pluginParam;
    }

    private AgentTaskStateReportScheduler reportScheduler(InMemoryWorkerTaskExecutionStore stateStore) {
        return new AgentTaskStateReportScheduler(stateStore, new NoopTaskResultReporter(),
                Executors.newSingleThreadScheduledExecutor(), List.of(), 15000L, 3);
    }
}
