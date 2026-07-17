package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ShellLocalPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class ShellLocalPluginTaskExecutorTest {

    /** Object mapper. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Temporary directory. */
    @TempDir
    private Path tempDir;

    @Test
    void shouldSubmitProcessInFixedWorkDirectoryAndCaptureFastExit() throws Exception {
        InMemoryWorkerTaskExecutionStore store = new InMemoryWorkerTaskExecutionStore();
        WorkerTaskExecutionSnap snapshot = snapshot("printf \"%s\" \"$DF_TEST_VALUE\"; printf \"%s\" err >&2");
        ObjectNode pluginEnv = OBJECT_MAPPER.createObjectNode().put("DF_TEST_VALUE", "plugin");
        ObjectNode taskEnv = OBJECT_MAPPER.createObjectNode().put("DF_TEST_VALUE", "task");
        ((ObjectNode) snapshot.getPluginParam()).set("env", pluginEnv);
        ((ObjectNode) snapshot.getTaskData()).set("env", taskEnv);
        String workDir = tempDir.resolve("runtime/task-1").toString();
        store.saveSnapshot(snapshot);
        WorkerTaskExecutionState actionState = WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1").status(StatusEnum.SUBMITTING).workDirPath(workDir).build();
        store.saveState(actionState, 0L);
        ShellLocalPluginTaskExecutor executor = executor(store);

        WorkerResult result = executor.submit(new RunningTaskContext(snapshot, actionState,
                null, null, workDir));

        assertEquals(StatusEnum.SUBMIT_SUCCESS, actionState.getStatus());
        assertEquals(workDir, result.getWorkDirPath());
        assertNull(result.getPluginLogUri());
        assertEquals("task", Files.readString(Path.of(workDir, "stdout.log")));
        assertEquals("err", Files.readString(Path.of(workDir, "stderr.log")));
        assertEquals(StatusEnum.RUN_SUCCESS, store.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldReturnControlTerminalWhenProcessDoesNotExist() {
        InMemoryWorkerTaskExecutionStore store = new InMemoryWorkerTaskExecutionStore();
        ShellLocalPluginTaskExecutor executor = executor(store);
        WorkerTaskExecutionState state = WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId("9223372036854775807")
                .workDirPath(tempDir.toString())
                .status(StatusEnum.STOPPING)
                .revision(1L)
                .build();
        RunningTaskContext context = new RunningTaskContext(snapshot(null), state,
                null, null, tempDir.toString());

        WorkerResult result = executor.stop(context);

        assertEquals(StatusEnum.STOP_SUCCESS, state.getStatus());
        assertEquals("9223372036854775807", result.getAppId());
        assertTrue(executor.finish(context));
    }

    private ShellLocalPluginTaskExecutor executor(InMemoryWorkerTaskExecutionStore store) {
        AgentProperties properties = new AgentProperties();
        properties.setPluginsRootDir(resolvePluginsRootDir());
        return new ShellLocalPluginTaskExecutor(new TemplateSpecRenderer(properties), Runnable::run,
                new WorkerTaskStateCoordinator(store));
    }

    private WorkerTaskExecutionSnap snapshot(String script) {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("command", "sh");
        pluginParam.putArray("args").add("-c");
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        if (script != null) {
            taskData.putArray("args").add(script);
        }
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("Shell")
                .workerId("worker-1")
                .pluginType(ShellLocalPluginTaskExecutor.PLUGIN_TYPE)
                .runMode(ShellLocalPluginTaskExecutor.RUN_MODE)
                .pluginParam(pluginParam)
                .taskData(taskData)
                .build();
    }

    private String resolvePluginsRootDir() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path moduleResourceDir = workingDir.resolve("src/main/resources");
        return Files.isDirectory(moduleResourceDir)
                ? moduleResourceDir.resolve("plugins").toString()
                : workingDir.resolve("datafusion-agent/src/main/resources/plugins").toString();
    }
}
