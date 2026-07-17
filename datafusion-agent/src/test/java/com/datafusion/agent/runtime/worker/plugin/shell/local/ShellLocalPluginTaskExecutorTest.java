package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ShellLocalPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/21
 * @since 1.0.0
 */
class ShellLocalPluginTaskExecutorTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Date formatter.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Temporary directory.
     */
    @TempDir
    private Path tempDir;

    @Test
    void shouldSubmitLocalShellTaskAndPersistRuntimeState() throws Exception {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        final ShellLocalPluginTaskExecutor executor = executor(stateStore);
        TaskRequest request = shellRequest("printf \"%s\" \"$DF_TEST_VALUE\"; printf \"%s\" err >&2");
        ObjectNode pluginEnv = OBJECT_MAPPER.createObjectNode();
        pluginEnv.put("DF_TEST_VALUE", "plugin");
        ObjectNode taskEnv = OBJECT_MAPPER.createObjectNode();
        taskEnv.put("DF_TEST_VALUE", "task");
        ((ObjectNode) request.getPluginParam()).set("env", pluginEnv);
        ((ObjectNode) request.getTaskData()).set("env", taskEnv);
        stateStore.saveSnapshot(snapshot(request));
        stateStore.saveState(WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .status(StatusEnum.SUBMITTING)
                .build(), 0L);

        TaskResult result = executor.submitTask(request);

        Path workDir = workDir();
        assertEquals(StatusEnum.SUBMIT_SUCCESS, result.getTaskState());
        assertEquals(workDir.toString(), result.getWorkerResult().getWorkDirPath());
        assertNull(result.getWorkerResult().getPluginLogUri());
        assertEquals("task", Files.readString(workDir.resolve("stdout.log")));
        assertEquals("err", Files.readString(workDir.resolve("stderr.log")));
        assertEquals(ShellLocalPluginTaskExecutor.PLUGIN_TYPE,
                stateStore.readSnapshot("task-1").orElseThrow().getPluginType());
        WorkerTaskExecutionState state = stateStore.readState("task-1").orElseThrow();
        assertEquals(0, state.getExitCode());
        assertEquals(result.getWorkerResult().getWorkDirPath(), state.getWorkDirPath());
        assertEquals(StatusEnum.RUN_SUCCESS, new ShellLocalRunModeStateMapping()
                .mapState(stateStore.readSnapshot("task-1").orElseThrow(), state));
    }

    @Test
    void shouldFinishWithMinimalRequest() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        TaskRequest submittedRequest = submittedRequest();
        stateStore.saveSnapshot(snapshot(submittedRequest));
        stateStore.saveState(state(StatusEnum.RUN_SUCCESS, "12345"), 0L);
        ShellLocalPluginTaskExecutor executor = executor(stateStore);

        TaskRequest minimalRequest = new TaskRequest();
        minimalRequest.setTaskInstanceId("task-1");
        boolean result = executor.finishTask(minimalRequest);

        assertTrue(result);
    }

    @Test
    void shouldRestoreSnapshotAndStateWhenStoppingWithMinimalRequest() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        TaskRequest submittedRequest = submittedRequest();
        stateStore.saveSnapshot(snapshot(submittedRequest));
        stateStore.saveState(state(StatusEnum.RUNNING, "9223372036854775807"), 0L);
        ShellLocalPluginTaskExecutor executor = executor(stateStore);

        TaskRequest minimalRequest = new TaskRequest();
        minimalRequest.setTaskInstanceId("task-1");
        TaskResult result = executor.stopTask(minimalRequest);

        assertEquals(StatusEnum.STOP_SUCCESS, result.getTaskState());
        assertEquals("flow-1", result.getFlowInstanceId());
        assertEquals("Shell", result.getTaskName());
        assertEquals("9223372036854775807", result.getWorkerResult().getAppId());
        assertEquals("/opt/datafusion/task-runtime/20260621/flow-1/task-1",
                result.getWorkerResult().getWorkDirPath());
        assertNull(result.getWorkerResult().getPluginLogUri());
        assertEquals(StatusEnum.STOP_SUCCESS, stateStore.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldRestoreSnapshotAndStateWhenKillingWithMinimalRequest() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        TaskRequest submittedRequest = submittedRequest();
        stateStore.saveSnapshot(snapshot(submittedRequest));
        stateStore.saveState(state(StatusEnum.RUNNING, "9223372036854775807"), 0L);
        ShellLocalPluginTaskExecutor executor = executor(stateStore);

        TaskRequest minimalRequest = new TaskRequest();
        minimalRequest.setTaskInstanceId("task-1");
        TaskResult result = executor.killTask(minimalRequest);

        assertEquals(StatusEnum.KILLED, result.getTaskState());
        assertEquals("flow-1", result.getFlowInstanceId());
        assertEquals("Shell", result.getTaskName());
        assertEquals("9223372036854775807", result.getWorkerResult().getAppId());
        assertEquals("/opt/datafusion/task-runtime/20260621/flow-1/task-1",
                result.getWorkerResult().getWorkDirPath());
        assertNull(result.getWorkerResult().getPluginLogUri());
        assertEquals(StatusEnum.KILLED, stateStore.readState("task-1").orElseThrow().getStatus());
    }

    private ShellLocalPluginTaskExecutor executor(InMemoryWorkerTaskExecutionStore stateStore) {
        AgentProperties properties = new AgentProperties();
        properties.getStorage().setTaskRuntimeDir(tempDir.resolve("task-runtime").toString());
        properties.setPluginsRootDir(resolvePluginsRootDir());
        Executor sameThreadExecutor = Runnable::run;
        return new ShellLocalPluginTaskExecutor(properties, stateStore, new TemplateSpecRenderer(properties), sameThreadExecutor);
    }

    private String resolvePluginsRootDir() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path moduleResourceDir = workingDir.resolve("src/main/resources");
        if (Files.isDirectory(moduleResourceDir)) {
            return moduleResourceDir.resolve("plugins").toString();
        }
        return workingDir.resolve("datafusion-agent/src/main/resources/plugins").toString();
    }

    private TaskRequest submittedRequest() {
        return shellRequest(null);
    }

    private TaskRequest shellRequest(String script) {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("command", "sh");
        pluginParam.putArray("args").add("-c");
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        if (script != null) {
            taskData.putArray("args").add(script);
        }
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("Shell");
        request.setWorkerResult(WorkerResult.builder()
                .workerId("worker-1")
                .build());
        request.setPluginType(ShellLocalPluginTaskExecutor.PLUGIN_TYPE);
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);
        return request;
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(ShellLocalPluginTaskExecutor.RUN_MODE)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private WorkerTaskExecutionState state(StatusEnum status, String appId) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .appId(appId)
                .workDirPath("/opt/datafusion/task-runtime/20260621/flow-1/task-1")
                .status(status)
                .build();
    }

    private Path workDir() {
        return tempDir.resolve("task-runtime")
                .resolve(LocalDate.now().format(DATE_FORMATTER))
                .resolve("flow-1")
                .resolve("task-1");
    }
}
