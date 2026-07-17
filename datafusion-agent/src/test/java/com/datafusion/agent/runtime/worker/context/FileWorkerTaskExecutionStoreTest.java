package com.datafusion.agent.runtime.worker.context;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FileWorkerTaskExecutionStore}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class FileWorkerTaskExecutionStoreTest {

    /**
     * Temporary directory.
     */
    @TempDir
    private Path tempDir;

    @Test
    void shouldRestoreOnlyCompleteManagerExecutions() throws Exception {
        FileWorkerTaskExecutionStore store = store();
        String taskOneDir = store.saveSnapshot(snapshot("task-1", "flow-1"));
        saveState(store, state("task-1", StatusEnum.RUNNING));
        String taskTwoDir = store.saveSnapshot(snapshot("task-2", "flow-2"));
        saveState(store, state("task-2", StatusEnum.RUNNING));
        String snapshotOnlyDir = store.saveSnapshot(snapshot("task-3", "flow-3"));
        Path stateOnlyDir = taskRuntimeRoot().resolve("state-only");
        WorkerTaskExecutionState stateOnly = state("task-4", StatusEnum.RUNNING);
        stateOnly.setWorkDirPath(stateOnlyDir.toString());
        saveState(store, stateOnly);

        FileWorkerTaskExecutionStore reloadedStore = store();
        Set<String> restoredIds = reloadedStore.restoreExecutions(
                List.of("task-1", "task-3", "task-4", "missing-task"));

        Files.delete(Path.of(taskTwoDir).resolve("task-2.state"));
        Files.delete(Path.of(snapshotOnlyDir).resolve("task-3.snap"));
        Files.delete(stateOnlyDir.resolve("task-4.state"));
        assertEquals(StatusEnum.RUNNING, reloadedStore.readState("task-1").orElseThrow().getStatus());
        assertTrue(reloadedStore.readState("task-2").isEmpty());
        assertTrue(reloadedStore.readSnapshot("task-3").isEmpty());
        assertTrue(reloadedStore.readState("task-4").isEmpty());
        assertEquals(Set.of("task-1"), restoredIds);
        assertEquals(taskOneDir,
                reloadedStore.readState("task-1").orElseThrow().getWorkDirPath());
    }

    @Test
    void shouldKeepLogsWhenDeletingExecution() throws Exception {
        FileWorkerTaskExecutionStore store = store();
        Path executionDir = Path.of(store.saveSnapshot(snapshot("task-1", "flow-1")));
        saveState(store, state("task-1", StatusEnum.RUNNING));
        saveState(store, state("task-1", StatusEnum.RUN_SUCCESS));
        Path stateLog = executionDir.resolve("state.log");
        Path jobFile = executionDir.resolve("job.json");
        Path nestedJsonFile = executionDir.resolve("nested").resolve("result.json");
        Files.writeString(jobFile, "{}");
        Files.createDirectories(nestedJsonFile.getParent());
        Files.writeString(nestedJsonFile, "{}");

        store.deleteExecution("task-1");

        assertTrue(Files.exists(stateLog));
        assertFalse(Files.exists(jobFile));
        assertTrue(Files.exists(nestedJsonFile));
        assertFalse(Files.exists(executionDir.resolve("task-1.state")));
        assertFalse(Files.exists(executionDir.resolve("task-1.snap")));
        assertTrue(store.readState("task-1").isEmpty());
    }

    @Test
    void shouldAppendStatusLogWhenReadStateCopyIsMutatedBeforeSave() throws Exception {
        FileWorkerTaskExecutionStore store = store();
        Path executionDir = Path.of(store.saveSnapshot(snapshot("task-1", "flow-1")));
        saveState(store, state("task-1", StatusEnum.RUNNING));

        WorkerTaskExecutionState nextState = store.readState("task-1").orElseThrow();
        nextState.setExitCode(0);
        nextState.setStatus(StatusEnum.RUN_SUCCESS);
        assertTrue(store.saveState(nextState, nextState.getRevision()));

        List<String> statusLines = Files.readAllLines(executionDir.resolve("state.log"));
        assertEquals(2, statusLines.size());
        assertTrue(statusLines.get(0).contains("revision:1|"));
        assertTrue(statusLines.get(1).contains("revision:2|"));
        assertTrue(statusLines.get(1).contains("status:RUN_SUCCESS|exitCode:0"));
    }

    @Test
    void shouldAppendStatusLogWhenAppIdChanges() throws Exception {
        FileWorkerTaskExecutionStore store = store();
        Path executionDir = Path.of(store.saveSnapshot(snapshot("task-1", "flow-1")));
        saveState(store, state("task-1", StatusEnum.RUNNING));

        WorkerTaskExecutionState nextState = state("task-1", StatusEnum.RUNNING);
        nextState.setAppId("200");
        saveState(store, nextState);

        List<String> statusLines = Files.readAllLines(executionDir.resolve("state.log"));
        assertEquals(2, statusLines.size());
        assertTrue(statusLines.get(1).contains("appId:200|revision:2|status:RUNNING"));
    }

    @Test
    void shouldSaveStateToRequestedWorkDirectoryWithoutSnapshot() {
        FileWorkerTaskExecutionStore store = store();
        Path executionDir = taskRuntimeRoot().resolve("custom-work-dir");
        WorkerTaskExecutionState state = state("task-1", StatusEnum.RUNNING);
        state.setWorkDirPath(executionDir.toString());

        saveState(store, state);

        assertTrue(Files.exists(executionDir.resolve("task-1.state")));
        assertEquals(executionDir.toString(), store.readState("task-1").orElseThrow().getWorkDirPath());
    }

    @Test
    void shouldOverwriteSnapshotAndReuseExistingExecutionDirectory() {
        FileWorkerTaskExecutionStore store = store();
        String initialDir = store.saveSnapshot(snapshot("task-1", "flow-1"));
        FileWorkerTaskExecutionStore reloadedStore = store();
        WorkerTaskExecutionSnap latestSnapshot = snapshot("task-1", "flow-2");
        latestSnapshot.setPluginType("DATAX");

        String latestDir = reloadedStore.saveSnapshot(latestSnapshot);

        assertEquals(initialDir, latestDir);
        assertEquals("DATAX", reloadedStore.readSnapshot("task-1").orElseThrow().getPluginType());
    }

    @Test
    void shouldNotMergeMissingFieldsFromOldState() {
        FileWorkerTaskExecutionStore store = store();
        String executionDir = store.saveSnapshot(snapshot("task-1", "flow-1"));
        WorkerTaskExecutionState existingState = state("task-1", StatusEnum.RUNNING);
        existingState.setWorkerId("worker-1");
        existingState.setExitCode(1);
        saveState(store, existingState);
        WorkerTaskExecutionState nextState = WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .status(StatusEnum.STOPPING)
                .build();

        assertTrue(store.saveState(nextState, existingState.getRevision()));

        WorkerTaskExecutionState savedState = store.readState("task-1").orElseThrow();
        assertNull(savedState.getAppId());
        assertNull(savedState.getWorkerId());
        assertNull(savedState.getExitCode());
        assertEquals(executionDir, savedState.getWorkDirPath());
        assertEquals(StatusEnum.STOPPING, savedState.getStatus());
        assertEquals(2L, savedState.getRevision());
    }

    @Test
    void shouldRejectStateWriteWhenExpectedRevisionIsStale() {
        FileWorkerTaskExecutionStore store = store();
        store.saveSnapshot(snapshot("task-1", "flow-1"));
        saveState(store, state("task-1", StatusEnum.RUNNING));
        WorkerTaskExecutionState staleState = store.readState("task-1").orElseThrow();
        WorkerTaskExecutionState latestState = state("task-1", StatusEnum.STOPPING);

        assertTrue(store.saveState(latestState, staleState.getRevision()));
        staleState.setStatus(StatusEnum.RUN_SUCCESS);

        assertFalse(store.saveState(staleState, staleState.getRevision()));
        WorkerTaskExecutionState persistedState = store.readState("task-1").orElseThrow();
        assertEquals(StatusEnum.STOPPING, persistedState.getStatus());
        assertEquals(2L, persistedState.getRevision());
    }

    @Test
    void shouldReturnDeepStateCopy() {
        FileWorkerTaskExecutionStore store = store();
        store.saveSnapshot(snapshot("task-1", "flow-1"));
        WorkerTaskExecutionState state = state("task-1", StatusEnum.RUNNING);
        state.setResult(JsonNodeFactory.instance.objectNode().put("message", "original"));
        saveState(store, state);

        WorkerTaskExecutionState firstRead = store.readState("task-1").orElseThrow();
        firstRead.setStatus(StatusEnum.RUN_FAILURE);
        ((ObjectNode) firstRead.getResult()).put("message", "mutated");
        WorkerTaskExecutionState secondRead = store.readState("task-1").orElseThrow();

        assertEquals(StatusEnum.RUNNING, secondRead.getStatus());
        assertEquals("original", secondRead.getResult().path("message").asText());
    }

    @Test
    void shouldCacheCopyOnlyAfterStateFileIsWritten() {
        FileWorkerTaskExecutionStore store = store();
        store.saveSnapshot(snapshot("task-1", "flow-1"));
        WorkerTaskExecutionState state = state("task-1", StatusEnum.RUNNING);

        saveState(store, state);
        state.setStatus(StatusEnum.RUN_FAILURE);

        assertEquals(StatusEnum.RUNNING, store.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldRejectInvalidState() {
        FileWorkerTaskExecutionStore store = store();

        assertFalse(store.saveState(null, 0L));
        assertFalse(store.saveState(new WorkerTaskExecutionState(), 0L));
    }

    private FileWorkerTaskExecutionStore store() {
        return new FileWorkerTaskExecutionStore(properties());
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.getStorage().setTaskRuntimeDir(taskRuntimeRoot().toString());
        return properties;
    }

    private Path taskRuntimeRoot() {
        return tempDir.resolve("task-runtime");
    }

    private WorkerTaskExecutionSnap snapshot(String taskInstanceId, String flowInstanceId) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(flowInstanceId)
                .taskInstanceId(taskInstanceId)
                .taskName("task-name")
                .pluginType("SHELL")
                .runMode("LOCAL")
                .build();
    }

    private WorkerTaskExecutionState state(String taskInstanceId, StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(taskInstanceId)
                .appId("100")
                .status(status)
                .build();
    }

    private void saveState(FileWorkerTaskExecutionStore store, WorkerTaskExecutionState state) {
        long expectedRevision = store.readState(state.getTaskInstanceId())
                .map(WorkerTaskExecutionState::getRevision)
                .orElse(0L);
        assertTrue(store.saveState(state, expectedRevision));
    }
}
