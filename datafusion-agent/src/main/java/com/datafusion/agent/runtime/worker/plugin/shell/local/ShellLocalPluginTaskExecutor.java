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
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
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
            log.info("SHELL LOCAL任务提交成功, taskInstanceId={}, appId={}, workDirPath={}, pluginLogUri={}",
                    request.getTaskInstanceId(), appId, workDirPath, pluginLogUri);
            WorkerResult requestWorkerResult = request.getWorkerResult();
            WorkerTaskExecutionState state = baseState(request, StatusEnum.RUNNING)
                    .appId(appId)
                    .workDirPath(workDirPath)
                    .result(resultJson(request, "LOCAL shell task submitted", pluginLogUri, null))
                    .build();
            stateStore.saveSnapshot(snapshot(request));
            stateStore.saveState(state);
            watchExit(process, state);
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.RUNNING)
                    .workerResult(workerResult(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                            appId, workDirPath, "LOCAL shell task submitted", pluginLogUri))
                    .build();
        } catch (Exception e) {
            log.warn("SHELL LOCAL任务提交失败, taskInstanceId={}, pluginLogUri={}",
                    request.getTaskInstanceId(), pluginLogUri(request), e);
            WorkerResult requestWorkerResult = request.getWorkerResult();
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.SUBMIT_FAILURE)
                    .workerResult(workerResult(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                            null, null, e.getMessage(), pluginLogUri(request)))
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
        WorkerResult workerResult = resolvedRequest.getWorkerResult();
        String workerId = workerResult == null ? null : workerResult.getWorkerId();
        if (status != null && !status.isFinalState()) {
            return TaskResult.builder()
                    .taskInstanceId(resolvedRequest.getTaskInstanceId())
                    .flowInstanceId(resolvedRequest.getFlowInstanceId())
                    .taskName(resolvedRequest.getTaskName())
                    .taskState(status)
                    .workerResult(workerResult(workerId, resolveAppId(resolvedRequest, state),
                            state == null ? null : state.getWorkDirPath(), "terminal task is not finished",
                            pluginLogUri(resolvedRequest, state)))
                    .build();
        }
        return TaskResult.builder()
                .taskInstanceId(resolvedRequest.getTaskInstanceId())
                .flowInstanceId(resolvedRequest.getFlowInstanceId())
                .taskName(resolvedRequest.getTaskName())
                .taskState(status == null ? StatusEnum.UNKNOWN : status)
                .workerResult(workerResult(workerId, resolveAppId(resolvedRequest, state),
                        state == null ? null : state.getWorkDirPath(), "finish checked",
                        pluginLogUri(resolvedRequest, state)))
                .build();
    }

    private TaskResult stopProcess(TaskRequest request, boolean forcibly) {
        WorkerTaskExecutionState state = currentState(request);
        TaskRequest resolvedRequest = resolveRequest(request);
        StatusEnum targetStatus = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        String appId = resolveAppId(resolvedRequest, state);
        ProcessHandle handle = processHandle(appId);
        String workDirPath = state == null ? null : state.getWorkDirPath();
        String pluginLogUri = pluginLogUri(resolvedRequest, state);
        WorkerResult workerResult = resolvedRequest.getWorkerResult();
        String workerId = workerResult == null ? null : workerResult.getWorkerId();
        if (handle == null) {
            recordControlResult(resolvedRequest, state, targetStatus, appId, workDirPath,
                    resultJson(resolvedRequest, "process not found", pluginLogUri, null));
            return TaskResult.builder()
                    .taskInstanceId(resolvedRequest.getTaskInstanceId())
                    .flowInstanceId(resolvedRequest.getFlowInstanceId())
                    .taskName(resolvedRequest.getTaskName())
                    .taskState(targetStatus)
                    .workerResult(workerResult(workerId, appId, workDirPath, "process not found", pluginLogUri))
                    .build();
        }
        if (forcibly) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
        }
        recordControlResult(resolvedRequest, state, targetStatus, appId, workDirPath,
                resultJson(resolvedRequest, forcibly ? "process killed" : "process stopped", pluginLogUri, null));
        return TaskResult.builder()
                .taskInstanceId(resolvedRequest.getTaskInstanceId())
                .flowInstanceId(resolvedRequest.getFlowInstanceId())
                .taskName(resolvedRequest.getTaskName())
                .taskState(targetStatus)
                .workerResult(workerResult(workerId, appId, workDirPath,
                        forcibly ? "process killed" : "process stopped", pluginLogUri))
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
        return Path.of(properties.getStorage().getTaskRuntimeDir(), date,
                firstText(request.getFlowInstanceId(), "unknown"), firstText(request.getTaskInstanceId(), "unknown"));
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
                latestState.setResult(resultJson(pluginType(latestState.getTaskInstanceId()),
                        "LOCAL process exited, exitCode=" + exitCode,
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
        WorkerResult requestWorkerResult = request.getWorkerResult();
        state.setStatus(status);
        state.setWorkerId(firstText(state.getWorkerId(), requestWorkerResult == null ? null : requestWorkerResult.getWorkerId()));
        state.setAppId(appId == null ? state.getAppId() : appId);
        state.setWorkDirPath(workDirPath == null ? state.getWorkDirPath() : workDirPath);
        state.setResult(result);
        stateStore.saveState(state);
    }

    private WorkerTaskExecutionState.WorkerTaskExecutionStateBuilder baseState(TaskRequest request, StatusEnum status) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                .appId(requestWorkerResult == null ? null : requestWorkerResult.getAppId())
                .status(status);
    }

    private WorkerTaskExecutionSnap snapshot(TaskRequest request) {
        WorkerResult requestWorkerResult = request.getWorkerResult();
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(request.getFlowInstanceId())
                .taskInstanceId(request.getTaskInstanceId())
                .taskName(request.getTaskName())
                .workerId(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId())
                .pluginType(pluginType(request))
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
        WorkerResult requestWorkerResult = request.getWorkerResult();
        resolvedRequest.setFlowInstanceId(firstText(request.getFlowInstanceId(), snapshot.getFlowInstanceId()));
        resolvedRequest.setTaskInstanceId(firstText(request.getTaskInstanceId(), snapshot.getTaskInstanceId()));
        resolvedRequest.setTaskName(firstText(request.getTaskName(), snapshot.getTaskName()));
        resolvedRequest.setPluginType(firstText(request.getPluginType(), snapshot.getPluginType()));
        resolvedRequest.setWorkerResult(WorkerResult.builder()
                .workerId(firstText(requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                        snapshot.getWorkerId()))
                .appId(requestWorkerResult == null ? null : requestWorkerResult.getAppId())
                .workDirPath(requestWorkerResult == null ? null : requestWorkerResult.getWorkDirPath())
                .message(requestWorkerResult == null ? null : requestWorkerResult.getMessage())
                .pluginLogUri(requestWorkerResult == null ? null : requestWorkerResult.getPluginLogUri())
                .build());
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

    private ObjectNode resultJson(TaskRequest request, String message, String pluginLogUri, Integer exitCode) {
        return resultJson(pluginType(request), message, pluginLogUri, exitCode);
    }

    private ObjectNode resultJson(String pluginType, String message, String pluginLogUri, Integer exitCode) {
        return PluginResultJson.build(message, pluginType, ShellLocalRunModeStateMapping.RUN_MODE, pluginLogUri,
                exitCode);
    }

    private String pluginType(TaskRequest request) {
        return request == null ? PLUGIN_TYPE : firstText(request.getPluginType(), PLUGIN_TYPE);
    }

    private String pluginType(String taskInstanceId) {
        if (taskInstanceId == null) {
            return PLUGIN_TYPE;
        }
        return stateStore.readSnapshot(taskInstanceId)
                .map(WorkerTaskExecutionSnap::getPluginType)
                .orElse(PLUGIN_TYPE);
    }

    private WorkerResult workerResult(String workerId, String appId, String workDirPath, String message,
            String pluginLogUri) {
        return WorkerResult.builder()
                .workerId(workerId)
                .appId(appId)
                .workDirPath(workDirPath)
                .message(message)
                .pluginLogUri(pluginLogUri)
                .build();
    }

    private String pluginLogUri(TaskRequest request) {
        if (request == null) {
            return null;
        }
        return firstText(text(request.getTaskData(), "pluginLogUri"), text(request.getPluginParam(), "pluginLogUri"));
    }

    private String pluginLogUri(TaskRequest request, WorkerTaskExecutionState state) {
        return firstText(text(state == null ? null : state.getResult(), "pluginLogUri"), pluginLogUri(request));
    }

    private String resolveAppId(TaskRequest request, WorkerTaskExecutionState state) {
        WorkerResult workerResult = request == null ? null : request.getWorkerResult();
        return firstText(state == null ? null : state.getAppId(), workerResult == null ? null : workerResult.getAppId());
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
