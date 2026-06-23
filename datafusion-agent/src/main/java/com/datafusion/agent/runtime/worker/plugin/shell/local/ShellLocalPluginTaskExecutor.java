package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.template.LocalProcessSpec;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateYamlFragments;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.TaskRuntimeFiles;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * SHELL 本地插件执行器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Slf4j
@Component
public class ShellLocalPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * 插件类型.
     */
    public static final String PLUGIN_TYPE = "SHELL";

    /**
     * 日期格式.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Local template path.
     */
    private static final String LOCAL_TEMPLATE_PATH = "plugins/shell/templates/shell-local-runtime.yml";

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * 任务执行状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Template renderer.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * Watcher executor.
     */
    private final Executor watcherExecutor;

    /**
     * 构造函数.
     *
     * @param properties agent 配置
     * @param stateStore      任务执行状态存储
     * @param templateRenderer 模板渲染器
     * @param watcherExecutor 进程监听线程池
     */
    public ShellLocalPluginTaskExecutor(AgentProperties properties, WorkerTaskExecutionStore stateStore,
            TemplateSpecRenderer templateRenderer, @Qualifier("agentTaskPool") Executor watcherExecutor) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.templateRenderer = templateRenderer;
        this.watcherExecutor = watcherExecutor;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public TaskRequest prepareTask(TaskRequest request) {
        localProcessSpec(request);
        return request;
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        try {
            LocalProcessSpec spec = localProcessSpec(request);
            ProcessBuilder processBuilder = new ProcessBuilder(spec.getCommand());
            Path workDir = workDir(request);
            Files.createDirectories(workDir);
            configureProcess(request, workDir, processBuilder);
            Path stdout = TaskRuntimeFiles.stdoutLog(workDir);
            Path stderr = TaskRuntimeFiles.stderrLog(workDir);
            processBuilder.redirectOutput(stdout.toFile());
            processBuilder.redirectError(stderr.toFile());
            Process process = processBuilder.start();
            String appId = String.valueOf(process.pid());
            String pluginLogUri = pluginLogUri(request);
            String workDirPath = workDir.toString();
            WorkerTaskExecutionState state = baseState(request, StatusEnum.RUNNING)
                    .appId(appId)
                    .workDirPath(workDirPath)
                    .result(resultJson("LOCAL shell task submitted", pluginLogUri, null))
                    .build();
            stateStore.saveSnapshot(snapshot(request));
            stateStore.saveState(state);
            watchExit(process, state);
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .workerId(request.getWorkerId())
                    .taskState(StatusEnum.RUNNING)
                    .appId(appId)
                    .workDirPath(workDirPath)
                    .result(resultJson("LOCAL shell task submitted", pluginLogUri, null))
                    .build();
        } catch (Exception e) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .workerId(request.getWorkerId())
                    .taskState(StatusEnum.SUBMIT_FAILURE)
                    .result(resultJson(e.getMessage(), request, null))
                    .build();
        }
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        return stopProcess(request, false);
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        return stopProcess(request, true);
    }

    @Override
    public TaskResult finishTask(TaskRequest request) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request);
        StatusEnum status = state == null ? StatusEnum.UNKNOWN : state.getStatus();
        if (status != null && !status.isFinalState()) {
            return TaskResult.builder()
                    .taskInstanceId(resolvedRequest.getTaskInstanceId())
                    .flowInstanceId(resolvedRequest.getFlowInstanceId())
                    .taskName(resolvedRequest.getTaskName())
                    .workerId(resolvedRequest.getWorkerId())
                    .taskState(status)
                    .appId(resolveAppId(resolvedRequest, state))
                    .workDirPath(workDirPath(state))
                    .result(resultJson("terminal task is not finished", pluginLogUri(resolvedRequest, state), null))
                    .build();
        }
        return TaskResult.builder()
                .taskInstanceId(resolvedRequest.getTaskInstanceId())
                .flowInstanceId(resolvedRequest.getFlowInstanceId())
                .taskName(resolvedRequest.getTaskName())
                .workerId(resolvedRequest.getWorkerId())
                .taskState(status == null ? StatusEnum.UNKNOWN : status)
                .appId(resolveAppId(resolvedRequest, state))
                .workDirPath(workDirPath(state))
                .result(resultJson("finish checked", pluginLogUri(resolvedRequest, state), null))
                .build();
    }

    private TaskResult stopProcess(TaskRequest request, boolean forcibly) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request);
        StatusEnum targetStatus = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        String appId = resolveAppId(resolvedRequest, state);
        ProcessHandle handle = processHandle(appId);
        String workDirPath = workDirPath(state);
        String pluginLogUri = pluginLogUri(resolvedRequest, state);
        if (handle == null) {
            recordControlResult(resolvedRequest, state, targetStatus, appId, workDirPath,
                    resultJson("process not found", pluginLogUri, null));
            return TaskResult.builder()
                    .taskInstanceId(resolvedRequest.getTaskInstanceId())
                    .flowInstanceId(resolvedRequest.getFlowInstanceId())
                    .taskName(resolvedRequest.getTaskName())
                    .workerId(resolvedRequest.getWorkerId())
                    .taskState(targetStatus)
                    .appId(appId)
                    .workDirPath(workDirPath)
                    .result(resultJson("process not found", pluginLogUri, null))
                    .build();
        }
        if (forcibly) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
        }
        recordControlResult(resolvedRequest, state, targetStatus, appId, workDirPath,
                resultJson(forcibly ? "process killed" : "process stopped", pluginLogUri, null));
        return TaskResult.builder()
                .taskInstanceId(resolvedRequest.getTaskInstanceId())
                .flowInstanceId(resolvedRequest.getFlowInstanceId())
                .taskName(resolvedRequest.getTaskName())
                .workerId(resolvedRequest.getWorkerId())
                .taskState(targetStatus)
                .appId(appId)
                .workDirPath(workDirPath)
                .result(resultJson(forcibly ? "process killed" : "process stopped", pluginLogUri, null))
                .build();
    }

    private void configureProcess(TaskRequest request, Path workDir, ProcessBuilder processBuilder) {
        processBuilder.directory(workDir.toFile());
        processBuilder.environment().putAll(mergeMap(object(request.getPluginParam(), "env"),
                object(request.getTaskData(), "env")));
    }

    private LocalProcessSpec localProcessSpec(TaskRequest request) {
        List<String> command = command(request);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("command", TemplateYamlFragments.listItems(command, 2));
        LocalProcessSpec spec = templateRenderer.renderYaml(LOCAL_TEMPLATE_PATH, values, LocalProcessSpec.class);
        if (spec.getCommand() == null || spec.getCommand().isEmpty()) {
            throw new IllegalArgumentException("SHELL LOCAL command不能为空");
        }
        return spec;
    }

    private List<String> command(TaskRequest request) {
        List<String> command = new ArrayList<>();
        String executable = firstText(text(request.getPluginParam(), "command"), text(request.getTaskData(), "command"));
        if (isBlank(executable)) {
            throw new IllegalArgumentException("pluginParam.command不能为空");
        }
        command.add(executable);
        command.addAll(list(request.getPluginParam(), "args"));
        command.addAll(list(request.getTaskData(), "args"));
        return command;
    }

    private Path workDir(TaskRequest request) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getStorage().getTaskRuntimeDir(), date, safePath(request.getFlowInstanceId()),
                safePath(request.getTaskInstanceId()));
    }

    private void watchExit(Process process, WorkerTaskExecutionState state) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                WorkerTaskExecutionState latestState = stateStore.readState(state.getTaskInstanceId()).orElse(state);
                if (latestState.getStatus() != null && latestState.getStatus().isFinalState()) {
                    return;
                }
                latestState.setExitCode(exitCode);
                latestState.setStatus(hasLiveDescendant(process) ? StatusEnum.RUNNING
                        : exitCode == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE);
                latestState.setResult(resultJson("LOCAL process exited, exitCode=" + exitCode,
                        pluginLogUri(null, latestState), exitCode));
                stateStore.saveState(latestState);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LOCAL process watcher interrupted, taskInstanceId={}", state.getTaskInstanceId(), e);
            }
        }, watcherExecutor);
    }

    private void recordControlResult(TaskRequest request, WorkerTaskExecutionState currentState, StatusEnum status,
            String appId, String workDirPath, JsonNode result) {
        WorkerTaskExecutionState state = currentState == null ? baseState(request, status).build() : currentState;
        state.setStatus(status);
        state.setWorkerId(firstText(state.getWorkerId(), request.getWorkerId()));
        state.setAppId(appId == null ? state.getAppId() : appId);
        state.setWorkDirPath(workDirPath == null ? state.getWorkDirPath() : workDirPath);
        state.setResult(result);
        stateStore.saveState(state);
    }

    private WorkerTaskExecutionState.WorkerTaskExecutionStateBuilder baseState(TaskRequest request, StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .workerId(request.getWorkerId())
                .appId(request.getAppId())
                .status(status);
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .workerId(request.getWorkerId())
                .pluginType(PLUGIN_TYPE)
                .runMode(ShellLocalRunModeStateMapping.RUN_MODE)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam())
                .build();
    }

    private WorkerTaskExecutionState currentState(TaskRequest request) {
        return request == null ? null : stateStore.readState(request.getTaskInstanceId()).orElse(null);
    }

    private TaskRequest resolveRequest(TaskRequest request) {
        if (request == null || request.getTaskInstanceId() == null) {
            return request;
        }
        WorkerTaskExecutionSnap snapshot = stateStore.readSnapshot(request.getTaskInstanceId()).orElse(null);
        if (snapshot == null) {
            return request;
        }
        TaskRequest resolvedRequest = new TaskRequest();
        resolvedRequest.setFlowInstanceId(firstText(request.getFlowInstanceId(), snapshot.getFlowInstanceId()));
        resolvedRequest.setTaskInstanceId(firstText(request.getTaskInstanceId(), snapshot.getTaskInstanceId()));
        resolvedRequest.setTaskName(firstText(request.getTaskName(), snapshot.getTaskName()));
        resolvedRequest.setWorkerId(firstText(request.getWorkerId(), snapshot.getWorkerId()));
        resolvedRequest.setPluginType(firstText(request.getPluginType(), snapshot.getPluginType()));
        resolvedRequest.setAppId(request.getAppId());
        resolvedRequest.setTaskState(request.getTaskState());
        resolvedRequest.setSubmitMode(request.getSubmitMode());
        resolvedRequest.setTaskData(request.getTaskData() == null ? snapshot.getTaskData() : request.getTaskData());
        resolvedRequest.setPluginParam(request.getPluginParam() == null ? snapshot.getPluginParam()
                : request.getPluginParam());
        return resolvedRequest;
    }

    private ProcessHandle processHandle(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return null;
        }
        try {
            return ProcessHandle.of(Long.parseLong(appId.trim())).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safePath(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private ObjectNode resultJson(String message, TaskRequest request, Integer exitCode) {
        return resultJson(message, pluginLogUri(request), exitCode);
    }

    private ObjectNode resultJson(String message, String pluginLogUri, Integer exitCode) {
        return PluginResultJson.build(message, PLUGIN_TYPE, ShellLocalRunModeStateMapping.RUN_MODE, pluginLogUri,
                exitCode);
    }

    private String pluginLogUri(TaskRequest request) {
        if (request == null) {
            return null;
        }
        return firstText(text(request.getTaskData(), "pluginLogUri"), text(request.getPluginParam(), "pluginLogUri"));
    }

    private String pluginLogUri(TaskRequest request, WorkerTaskExecutionState state) {
        return firstText(resultText(state == null ? null : state.getResult(), "pluginLogUri"), pluginLogUri(request));
    }

    private String workDirPath(WorkerTaskExecutionState state) {
        return state == null ? null : state.getWorkDirPath();
    }

    private String resolveAppId(TaskRequest request, WorkerTaskExecutionState state) {
        return firstText(state == null ? null : state.getAppId(), request == null ? null : request.getAppId());
    }

    private JsonNode object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject() ? value : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String resultText(JsonNode node, String field) {
        return text(node, field);
    }

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        List<String> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private Map<String, String> mergeMap(JsonNode... nodes) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            if (node != null && node.isObject()) {
                node.properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asText()));
            }
        }
        return result;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean hasLiveDescendant(Process process) {
        return process.toHandle().descendants().anyMatch(ProcessHandle::isAlive);
    }
}
