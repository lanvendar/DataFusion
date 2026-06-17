package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FileWorkerTaskExecutionStore}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/17
 * @since 1.0.0
 */
class FileWorkerTaskExecutionStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldCacheStatesAndKeepLogWhenRemovingRuntimeFiles() throws Exception {
        FileWorkerTaskExecutionStore store = new FileWorkerTaskExecutionStore(properties());
        store.saveSnapshot(snapshot());
        store.saveState(state(StatusEnum.RUNNING));
        store.saveState(state(StatusEnum.RUNNING));
        store.saveState(state(StatusEnum.RUN_SUCCESS));

        Path executionDir = executionDir();
        Path logFile = executionDir.resolve("task-1.log");
        assertEquals(1, store.listListeningStates().size());
        assertEquals(2, Files.readAllLines(logFile).size());

        FileWorkerTaskExecutionStore reloadedStore = new FileWorkerTaskExecutionStore(properties());
        assertEquals(1, reloadedStore.listListeningStates().size());
        assertEquals(StatusEnum.RUN_SUCCESS, reloadedStore.readState("task-1").orElseThrow().getStatus());

        reloadedStore.remove("task-1");

        assertTrue(Files.exists(logFile));
        assertFalse(Files.exists(executionDir.resolve("task-1.state")));
        assertFalse(Files.exists(executionDir.resolve("task-1.snap")));
        assertEquals(0, reloadedStore.listListeningStates().size());
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setModules(tempDir.toString());
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

    private Path executionDir() {
        return tempDir.resolve("task-runtime")
                .resolve(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE))
                .resolve("flow-1")
                .resolve("task-1");
    }
}
