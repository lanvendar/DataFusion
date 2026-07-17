package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.template.LocalProcessSpec;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateYamlFragments;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRuntimeFiles;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * SHELL 本地插件执行器.
 *
 * <p>执行器只操作动作级候选状态。本地进程退出是一次性事件，由状态协调器按 action revision 和 appId
 * 与同步提交结果竞争，不直接读写任务执行存储。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
@Component
public class ShellLocalPluginTaskExecutor implements PluginTaskExecutor {

    /** 插件类型. */
    public static final String PLUGIN_TYPE = "SHELL";

    /** 运行模式. */
    public static final String RUN_MODE = "LOCAL";

    /** Local template path. */
    private static final String LOCAL_TEMPLATE_PATH = "shell/templates/shell-local-runtime.yml";

    /** Template renderer. */
    private final TemplateSpecRenderer templateRenderer;

    /** Process watcher executor. */
    private final Executor watcherExecutor;

    /** Worker state coordinator. */
    private final WorkerTaskStateCoordinator stateCoordinator;

    /**
     * 创建 SHELL LOCAL 执行器.
     *
     * @param templateRenderer 模板渲染器
     * @param watcherExecutor  进程监听线程池
     * @param stateCoordinator 状态协调器
     */
    public ShellLocalPluginTaskExecutor(TemplateSpecRenderer templateRenderer,
            @Qualifier("agentTaskPool") Executor watcherExecutor,
            WorkerTaskStateCoordinator stateCoordinator) {
        this.templateRenderer = templateRenderer;
        this.watcherExecutor = watcherExecutor;
        this.stateCoordinator = stateCoordinator;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return RUN_MODE;
    }

    @Override
    public void validate(RunningTaskContext context) {
        localProcessSpec(context.getSnapshot());
    }

    @Override
    public WorkerResult submit(RunningTaskContext context) {
        WorkerTaskExecutionSnap snapshot = context.getSnapshot();
        WorkerTaskExecutionState state = context.getExecutionState();
        Path workDir = Path.of(context.getWorkDirPath());
        String pluginLogUri = pluginLogUri(snapshot);
        try {
            LocalProcessSpec spec = localProcessSpec(snapshot);
            Files.createDirectories(workDir);
            ProcessBuilder processBuilder = new ProcessBuilder(spec.getCommand());
            processBuilder.directory(workDir.toFile());
            processBuilder.environment().putAll(mergeMap(object(snapshot.getPluginParam(), "env"),
                    object(snapshot.getTaskData(), "env")));
            processBuilder.redirectOutput(TaskRuntimeFiles.stdoutLog(workDir).toFile());
            processBuilder.redirectError(TaskRuntimeFiles.stderrLog(workDir).toFile());
            Process process = processBuilder.start();
            String appId = String.valueOf(process.pid());

            // watcher 必须在 submit 返回前注册，并保留原 SUBMITTING revision，才能接住快速退出。
            WorkerTaskExecutionState actionState = state.copy();
            actionState.setAppId(appId);
            actionState.setWorkDirPath(workDir.toString());
            watchExit(process, snapshot, actionState, pluginLogUri);

            applyResult(state, StatusEnum.SUBMIT_SUCCESS, appId, workDir.toString(),
                    resultJson(snapshot.getPluginType(), "LOCAL shell task submitted", pluginLogUri, null));
            return workerResult(state);
        } catch (Exception e) {
            log.warn("SHELL LOCAL任务提交失败, taskInstanceId={}", snapshot.getTaskInstanceId(), e);
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, workDir.toString(),
                    resultJson(snapshot.getPluginType(), e.getMessage(), pluginLogUri, null));
            return workerResult(state);
        }
    }

    @Override
    public WorkerResult stop(RunningTaskContext context) {
        return stopProcess(context, false);
    }

    @Override
    public WorkerResult kill(RunningTaskContext context) {
        return stopProcess(context, true);
    }

    @Override
    public boolean finish(RunningTaskContext context) {
        return true;
    }

    private WorkerResult stopProcess(RunningTaskContext context, boolean forcibly) {
        WorkerTaskExecutionState state = context.getExecutionState();
        ProcessHandle handle = processHandle(state.getAppId());
        if (handle != null) {
            if (forcibly) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        }
        StatusEnum status = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        String message = handle == null ? "process not found" : forcibly ? "process killed" : "process stopped";
        String pluginLogUri = pluginLogUri(context.getSnapshot(), state);
        applyResult(state, status, state.getAppId(), context.getWorkDirPath(),
                resultJson(context.getSnapshot().getPluginType(), message, pluginLogUri, null));
        return workerResult(state);
    }

    private void watchExit(Process process, WorkerTaskExecutionSnap snapshot,
            WorkerTaskExecutionState actionState, String pluginLogUri) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                WorkerTaskExecutionState exitState = actionState.copy();
                exitState.setExitCode(exitCode);
                exitState.setStatus(hasLiveDescendant(process) ? StatusEnum.RUNNING
                        : exitCode == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE);
                exitState.setResult(resultJson(snapshot.getPluginType(),
                        "LOCAL process exited, exitCode=" + exitCode, pluginLogUri, exitCode));
                stateCoordinator.commitLocalProcessExit(actionState, exitState);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LOCAL process watcher interrupted, taskInstanceId={}", actionState.getTaskInstanceId(), e);
            } catch (RuntimeException e) {
                log.warn("LOCAL process watcher failed, taskInstanceId={}", actionState.getTaskInstanceId(), e);
            }
        }, watcherExecutor);
    }

    private LocalProcessSpec localProcessSpec(WorkerTaskExecutionSnap snapshot) {
        List<String> command = new ArrayList<>();
        String executable = firstText(text(snapshot.getPluginParam(), "command"),
                text(snapshot.getTaskData(), "command"));
        if (isBlank(executable)) {
            throw new IllegalArgumentException("pluginParam.command不能为空");
        }
        command.add(executable);
        command.addAll(list(snapshot.getPluginParam(), "args"));
        command.addAll(list(snapshot.getTaskData(), "args"));
        LocalProcessSpec spec = templateRenderer.renderYaml(LOCAL_TEMPLATE_PATH,
                Map.of("command", TemplateYamlFragments.listItems(command, 2)), LocalProcessSpec.class);
        if (spec.getCommand() == null || spec.getCommand().isEmpty()) {
            throw new IllegalArgumentException("SHELL LOCAL command不能为空");
        }
        return spec;
    }

    private void applyResult(WorkerTaskExecutionState state, StatusEnum status, String appId,
            String workDirPath, JsonNode result) {
        state.setStatus(status);
        state.setAppId(appId);
        state.setWorkDirPath(workDirPath);
        state.setResult(result);
    }

    private WorkerResult workerResult(WorkerTaskExecutionState state) {
        return WorkerResult.builder()
                .workerId(state.getWorkerId())
                .appId(state.getAppId())
                .workDirPath(state.getWorkDirPath())
                .message(text(state.getResult(), "message"))
                .pluginLogUri(text(state.getResult(), "pluginLogUri"))
                .outputVars(state.getOutputVars())
                .build();
    }

    private ProcessHandle processHandle(String appId) {
        if (isBlank(appId)) {
            return null;
        }
        try {
            return ProcessHandle.of(Long.parseLong(appId.trim())).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ObjectNode resultJson(String pluginType, String message, String pluginLogUri, Integer exitCode) {
        return PluginResultJson.build(message, firstText(pluginType, PLUGIN_TYPE), RUN_MODE, pluginLogUri, exitCode);
    }

    private String pluginLogUri(WorkerTaskExecutionSnap snapshot) {
        return firstText(text(snapshot.getTaskData(), "pluginLogUri"),
                text(snapshot.getPluginParam(), "pluginLogUri"));
    }

    private String pluginLogUri(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return firstText(text(state.getResult(), "pluginLogUri"), pluginLogUri(snapshot));
    }

    private JsonNode object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject() ? value : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        List<String> values = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private Map<String, String> mergeMap(JsonNode... nodes) {
        Map<String, String> values = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            if (node != null && node.isObject()) {
                node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
            }
        }
        return values;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean hasLiveDescendant(Process process) {
        try {
            return process.toHandle().descendants().anyMatch(ProcessHandle::isAlive);
        } catch (RuntimeException e) {
            log.debug("无法读取 LOCAL 进程子进程, pid={}", process.pid(), e);
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
