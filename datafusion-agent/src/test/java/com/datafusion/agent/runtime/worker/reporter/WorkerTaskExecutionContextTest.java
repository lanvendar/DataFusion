package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.context.WorkerTaskExecutionContext;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
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
        saveState(store, state(StatusEnum.RUNNING));
        saveState(store, state(StatusEnum.RUNNING));
        saveState(store, state(StatusEnum.RUN_SUCCESS));

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
        saveState(store, state(StatusEnum.RUNNING));

        WorkerTaskExecutionState state = store.readState("task-1").orElseThrow();
        state.setExitCode(0);
        state.setStatus(StatusEnum.RUN_SUCCESS);
        assertTrue(store.saveState(state, state.getRevision()));

        Path logFile = executionDir().resolve("state.log");
        List<String> statusLines = Files.readAllLines(logFile);
        assertEquals(2, statusLines.size());
        assertTrue(statusLines.get(0).contains("revision:1|"));
        assertTrue(statusLines.get(1).contains("revision:2|"));
        assertTrue(statusLines.get(1).contains("status:RUN_SUCCESS|exitCode:0"));
    }

    @Test
    void shouldAppendStatusLogWhenAppIdChanges() throws Exception {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        saveState(store, state(StatusEnum.RUNNING));

        WorkerTaskExecutionState state = state(StatusEnum.RUNNING);
        state.setAppId("200");
        saveState(store, state);

        Path logFile = executionDir().resolve("state.log");
        assertEquals(2, Files.readAllLines(logFile).size());
        assertTrue(Files.readString(logFile).contains("appId:200|revision:2|status:RUNNING"));
    }

    @Test
    void shouldRemoveContextWithoutDeletingRuntimeFiles() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        saveState(store, state(StatusEnum.RUN_FAILURE));

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
        saveState(store, state(StatusEnum.RUN_FAILURE));
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

    @Test
    void shouldRemoveCachedContextWhenRuntimeFilesDoNotExist() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        TaskRequest request = new TaskRequest();
        request.setTaskInstanceId("task-without-files");
        store.getOrCreate(request);

        store.deleteExecution(request.getTaskInstanceId());

        assertNull(store.get(request.getTaskInstanceId()));
    }

    @Test
    void shouldDeleteRuntimeFilesFromSharedDirectoryWhenContextWasRemoved() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        saveState(store, state(StatusEnum.RUN_FAILURE));
        store.removeContext("task-1");

        store.deleteExecution("task-1");

        assertFalse(Files.exists(executionDir().resolve("task-1.state")));
        assertFalse(Files.exists(executionDir().resolve("task-1.snap")));
    }

    @Test
    void shouldSaveStateToItsWorkDirectoryWithoutSnapshot() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        WorkerTaskExecutionState state = state(StatusEnum.RUNNING);
        state.setWorkDirPath(executionDir().toString());

        saveState(store, state);

        assertTrue(Files.exists(executionDir().resolve("task-1.state")));
        assertEquals(executionDir().toString(), store.readState("task-1").orElseThrow().getWorkDirPath());
    }

    @Test
    void shouldOverwriteSnapshotOnEachSubmit() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        WorkerTaskExecutionSnap latestSnapshot = snapshot();
        latestSnapshot.setPluginType("DATAX");

        store.saveSnapshot(latestSnapshot);

        assertEquals("DATAX", store.readSnapshot("task-1").orElseThrow().getPluginType());
        assertEquals("DATAX", store.get("task-1").getPluginType());
    }

    @Test
    void shouldOverwriteSnapshotWhenSavingContext() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.save(RunningTaskContext.fromSnapshotAndState(snapshot(), state(StatusEnum.RUNNING)));
        WorkerTaskExecutionSnap latestSnapshot = snapshot();
        latestSnapshot.setPluginType("DATAX");

        store.save(RunningTaskContext.fromSnapshotAndState(latestSnapshot, state(StatusEnum.RUNNING)));

        assertEquals("DATAX", store.readSnapshot("task-1").orElseThrow().getPluginType());
        assertEquals("DATAX", store.get("task-1").getPluginType());
    }

    @Test
    void shouldMergeMissingStateFieldsInsideStateLock() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        WorkerTaskExecutionState existingState = state(StatusEnum.RUNNING);
        existingState.setWorkerId("worker-1");
        existingState.setWorkDirPath(executionDir().toString());
        existingState.setExitCode(1);
        saveState(store, existingState);
        WorkerTaskExecutionState nextState = WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .status(StatusEnum.STOPPING)
                .revision(existingState.getRevision())
                .build();

        store.save(RunningTaskContext.fromSnapshotAndState(snapshot(), nextState));

        WorkerTaskExecutionState savedState = store.readState("task-1").orElseThrow();
        assertEquals("100", savedState.getAppId());
        assertEquals("worker-1", savedState.getWorkerId());
        assertEquals(executionDir().toString(), savedState.getWorkDirPath());
        assertEquals(1, savedState.getExitCode());
        assertEquals(2L, savedState.getRevision());
        assertEquals(StatusEnum.STOPPING, store.get("task-1").getTaskState());
    }

    @Test
    void shouldRejectStateWriteWhenExpectedRevisionIsStale() {
        WorkerTaskExecutionContext store = new WorkerTaskExecutionContext(properties());
        store.saveSnapshot(snapshot());
        saveState(store, state(StatusEnum.RUNNING));
        WorkerTaskExecutionState staleState = store.readState("task-1").orElseThrow();
        WorkerTaskExecutionState latestState = state(StatusEnum.STOPPING);

        assertTrue(store.saveState(latestState, staleState.getRevision()));
        staleState.setStatus(StatusEnum.RUN_SUCCESS);

        assertFalse(store.saveState(staleState, staleState.getRevision()));
        WorkerTaskExecutionState persistedState = store.readState("task-1").orElseThrow();
        assertEquals(StatusEnum.STOPPING, persistedState.getStatus());
        assertEquals(2L, persistedState.getRevision());
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

    private void saveState(WorkerTaskExecutionContext store, WorkerTaskExecutionState state) {
        long expectedRevision = store.readState(state.getTaskInstanceId())
                .map(WorkerTaskExecutionState::getRevision)
                .orElse(0L);
        assertTrue(store.saveState(state, expectedRevision));
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
