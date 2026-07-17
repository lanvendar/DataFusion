package com.datafusion.agent.runtime.worker.plugin.spider.local;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.InMemoryWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalRunModeStateMapping;
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
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link SpiderLocalPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/29
 * @since 1.0.0
 */
class SpiderLocalPluginTaskExecutorTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Temporary directory.
     */
    @TempDir
    private Path tempDir;

    @Test
    void shouldDelegateShellLocalExecutionAndPersistSpiderPluginType() {
        InMemoryWorkerTaskExecutionStore stateStore = new InMemoryWorkerTaskExecutionStore();
        SpiderLocalPluginTaskExecutor executor = spiderExecutor(stateStore);
        TaskRequest request = spiderRequest();
        stateStore.saveSnapshot(WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .pluginType(request.getPluginType())
                .runMode(SpiderLocalPluginTaskExecutor.RUN_MODE)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build());
        stateStore.saveState(WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .status(StatusEnum.SUBMITTING)
                .build(), 0L);

        TaskResult result = executor.submitTask(request);

        assertEquals(SpiderLocalPluginTaskExecutor.PLUGIN_TYPE, executor.pluginType());
        assertEquals(StatusEnum.SUBMIT_SUCCESS, result.getTaskState());
        assertEquals(SpiderLocalPluginTaskExecutor.PLUGIN_TYPE,
                stateStore.readSnapshot("task-1").orElseThrow().getPluginType());
        assertEquals(SpiderLocalPluginTaskExecutor.RUN_MODE,
                stateStore.readSnapshot("task-1").orElseThrow().getRunMode());
        assertEquals(SpiderLocalPluginTaskExecutor.PLUGIN_TYPE,
                stateStore.readState("task-1").orElseThrow().getResult().get("pluginType").asText());
    }

    @Test
    void shouldDelegateShellLocalStateMapping() {
        SpiderLocalRunModeStateMapping mapping = new SpiderLocalRunModeStateMapping(new ShellLocalRunModeStateMapping());

        assertEquals(SpiderLocalPluginTaskExecutor.PLUGIN_TYPE, mapping.pluginType());
        assertEquals(SpiderLocalPluginTaskExecutor.RUN_MODE, mapping.runMode());
    }

    private SpiderLocalPluginTaskExecutor spiderExecutor(InMemoryWorkerTaskExecutionStore stateStore) {
        AgentProperties properties = new AgentProperties();
        properties.getStorage().setTaskRuntimeDir(tempDir.resolve("task-runtime").toString());
        properties.setPluginsRootDir(resolvePluginsRootDir());
        Executor sameThreadExecutor = Runnable::run;
        ShellLocalPluginTaskExecutor shellExecutor = new ShellLocalPluginTaskExecutor(properties, stateStore,
                new TemplateSpecRenderer(properties), sameThreadExecutor);
        return new SpiderLocalPluginTaskExecutor(shellExecutor);
    }

    private String resolvePluginsRootDir() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path moduleResourceDir = workingDir.resolve("src/main/resources");
        if (Files.isDirectory(moduleResourceDir)) {
            return moduleResourceDir.resolve("plugins").toString();
        }
        return workingDir.resolve("datafusion-agent/src/main/resources/plugins").toString();
    }

    private TaskRequest spiderRequest() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("command", "sh");
        pluginParam.putArray("args").add("-c");
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.putArray("args").add("printf spider");
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("Spider");
        request.setWorkerResult(WorkerResult.builder()
                .workerId("worker-1")
                .build());
        request.setPluginType(SpiderLocalPluginTaskExecutor.PLUGIN_TYPE);
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);
        return request;
    }
}
