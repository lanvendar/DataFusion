package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.context.WorkerTaskExecutionContext;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link WorkerTaskExecutionContext}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/17
 * @since 1.0.0
 */
class WorkerTaskExecutionContextTest {

    /**
     * Temporary directory.
     */
    @TempDir
    private Path tempDir;

    @Test
    void shouldRestoreStateOnlyByManagerTaskRequestsAndKeepLogWhenRemovingRuntimeFiles() throws Exception {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        store.saveState(state(StatusEnum.RUNNING));
        store.saveState(state(StatusEnum.RUNNING));
        store.saveState(state(StatusEnum.RUN_SUCCESS));

        Path executionDir = executionDir();
        final Path logFile = executionDir.resolve("state.log");
        Path jobFile = executionDir.resolve("job.json");
        Path apiJobFile = executionDir.resolve("api-job.json");
        Path nestedJsonFile = executionDir.resolve("nested").resolve("result.json");
        Files.writeString(jobFile, "{}");
        Files.writeString(apiJobFile, "{}");
        Files.createDirectories(nestedJsonFile.getParent());
        Files.writeString(nestedJsonFile, "{}");
        assertNotNull(store.get("task-1"));
        assertEquals(3L, store.readState("task-1").orElseThrow().getRevision());
        assertEquals(2, Files.readAllLines(logFile).size());

        WorkerTaskExecutionContext reloadedStore = new WorkerTaskExecutionContext(properties());
        assertNull(reloadedStore.get("task-1"));

        reloadedStore.restoreListeningTasks(List.of(restoreRequest()));
        assertNotNull(reloadedStore.get("task-1"));
        assertEquals(StatusEnum.RUN_SUCCESS, reloadedStore.readState("task-1").orElseThrow().getStatus());

        reloadedStore.deleteExecution("task-1");

        assertTrue(Files.exists(logFile));
        assertFalse(Files.exists(jobFile));
        assertFalse(Files.exists(apiJobFile));
        assertTrue(Files.exists(nestedJsonFile));
        assertFalse(Files.exists(executionDir.resolve("task-1.state")));
        assertFalse(Files.exists(executionDir.resolve("task-1.snap")));
        assertNull(reloadedStore.get("task-1"));
    }

    @Test
    void shouldAppendStatusLogWhenReadStateIsMutatedBeforeSave() throws Exception {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        store.saveState(state(StatusEnum.RUNNING));

        WorkerTaskExecutionState state = store.readState("task-1").orElseThrow();
        state.setExitCode(0);
        state.setStatus(StatusEnum.RUN_SUCCESS);
        store.saveState(state);

        Path logFile = executionDir().resolve("state.log");
        assertEquals(2, Files.readAllLines(logFile).size());
        assertTrue(Files.readString(logFile).contains("status:RUN_SUCCESS|exitCode:0"));
    }

    @Test
    void shouldAppendStatusLogWhenAppIdChanges() throws Exception {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        store.saveState(state(StatusEnum.RUNNING));

        WorkerTaskExecutionState state = state(StatusEnum.RUNNING);
        state.setAppId("200");
        store.saveState(state);

        Path logFile = executionDir().resolve("state.log");
        assertEquals(2, Files.readAllLines(logFile).size());
        assertTrue(Files.readString(logFile).contains("appId:200|status:RUNNING"));
    }

    @Test
    void shouldRemoveContextWithoutDeletingRuntimeFiles() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        store.saveState(state(StatusEnum.RUN_FAILURE));

        store.removeContext("task-1");

        Path executionDir = executionDir();
        assertNull(store.get("task-1"));
        assertTrue(Files.exists(executionDir.resolve("task-1.state")));
        assertTrue(Files.exists(executionDir.resolve("task-1.snap")));
    }

    @Test
    void shouldNotRestoreContextWhenReadingRemovedRuntimeFiles() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        store.saveState(state(StatusEnum.RUN_FAILURE));
        store.removeContext("task-1");

        assertEquals(StatusEnum.RUN_FAILURE, store.readState("task-1").orElseThrow().getStatus());
        assertTrue(store.readSnapshot("task-1").isPresent());

        assertNull(store.get("task-1"));
    }

    @Test
    void shouldNotPersistRuntimeFilesWhenContextIsOnlyCreated() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());

        store.getOrCreate(restoreRequest());

        assertFalse(Files.exists(executionDir().resolve("task-1.state")));
        assertFalse(Files.exists(executionDir().resolve("task-1.snap")));
        assertNotNull(store.get("task-1"));
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setModules(tempDir.toString());
        properties.getStorage().setTaskRuntimeDir(tempDir.resolve("task-runtime").toString());
        return properties;
    }

    private WorkerTaskExecutionSnap snapshot() {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("task-name")
                .pluginType("SHELL")
                .runMode("LOCAL")
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("100")
                .status(status)
                .build();
    }

    private TaskRequest restoreRequest() {
        TaskRequest request = new TaskRequest();
        request.setTaskInstanceId("task-1");
        request.setWorkerResult(WorkerResult.builder()
                .workDirPath(executionDir().toString())
                .build());
        return request;
    }

    private Path executionDir() {
        return tempDir.resolve("task-runtime")
                .resolve(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE))
                .resolve("flow-1")
                .resolve("task-1");
    }
}
