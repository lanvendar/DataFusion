package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.template.LocalProcessSpec;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateYamlFragments;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;
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
    private final WorkerTaskExecutionStateStore stateStore;

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
    public ShellLocalPluginTaskExecutor(AgentProperties properties, WorkerTaskExecutionStateStore stateStore,
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
            configureProcess(spec, processBuilder);
            Path logDir = logDir(request);
            Files.createDirectories(logDir);
            processBuilder.redirectOutput(Path.of(spec.getStdout()).toFile());
            processBuilder.redirectError(Path.of(spec.getStderr()).toFile());
            Process process = processBuilder.start();
            String appId = String.valueOf(process.pid());
            WorkerTaskExecutionState state = baseState(request, StatusEnum.RUNNING)
                    .appId(appId)
                    .logPath(logDir.toString())
                    .result(resultJson("LOCAL shell task submitted", spec.getPluginLogUri(), logDir.toString(), null))
                    .build();
            stateStore.record(state);
            watchExit(process, state);
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.RUNNING)
                    .appId(appId)
                    .logPath(logDir.toString())
                    .result(resultJson("LOCAL shell task submitted", spec.getPluginLogUri(), logDir.toString(), null))
                    .build();
        } catch (Exception e) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.SUBMIT_FAILURE)
                    .result(resultJson(e.getMessage(), request, null, null))
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
        StatusEnum state = queryLocalState(request);
        if (state != null && !state.isFinalState()) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(state)
                    .appId(request.getAppId())
                    .result(resultJson("terminal task is not finished", request, null, null))
                    .build();
        }
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(state == null ? StatusEnum.UNKNOWN : state)
                .appId(request.getAppId())
                .result(resultJson("finish checked", request, null, null))
                .build();
    }

    private TaskResult stopProcess(TaskRequest request, boolean forcibly) {
        StatusEnum targetStatus = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        ProcessHandle handle = processHandle(request.getAppId());
        if (handle == null) {
            recordControlResult(request, targetStatus, resultJson("process not found", request, null, null));
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(targetStatus)
                    .appId(request.getAppId())
                    .result(resultJson("process not found", request, null, null))
                    .build();
        }
        if (forcibly) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
        }
        recordControlResult(request, targetStatus,
                resultJson(forcibly ? "process killed" : "process stopped", request, null, null));
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(targetStatus)
                .appId(request.getAppId())
                .result(resultJson(forcibly ? "process killed" : "process stopped", request, null, null))
                .build();
    }

    private void configureProcess(LocalProcessSpec spec, ProcessBuilder processBuilder) {
        if (!isBlank(spec.getWorkDir())) {
            processBuilder.directory(Path.of(spec.getWorkDir()).toFile());
        }
        if (spec.getEnv() != null) {
            processBuilder.environment().putAll(spec.getEnv());
        }
    }

    private LocalProcessSpec localProcessSpec(TaskRequest request) {
        List<String> command = command(request);
        Map<String, String> values = new LinkedHashMap<>();
        Path logDir = logDir(request);
        values.put("workDir", firstText(text(request.getTaskData(), "workDir"),
                text(request.getPluginParam(), "workDir"), ""));
        values.put("command", TemplateYamlFragments.listItems(command, 2));
        values.put("env", TemplateYamlFragments.mapEntries(mergeMap(object(request.getPluginParam(), "env"),
                object(request.getTaskData(), "env")), 2));
        values.put("stdout", logDir.resolve("stdout.log").toString());
        values.put("stderr", logDir.resolve("stderr.log").toString());
        values.put("pluginLogUri", firstText(text(request.getTaskData(), "pluginLogUri"),
                text(request.getPluginParam(), "pluginLogUri"), ""));
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

    private Path logDir(TaskRequest request) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getModules(), properties.getStorage().getLogsDir(), date,
                safePath(request.getFlowInstanceId()), safePath(request.getTaskInstanceId()));
    }

    private void watchExit(Process process, WorkerTaskExecutionState state) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                WorkerTaskExecutionState latestState = stateStore.read(state.getTaskInstanceId()).orElse(state);
                if (latestState.getStatus() != null && latestState.getStatus().isFinalState()) {
                    return;
                }
                latestState.setExitCode(exitCode);
                latestState.setStatus(exitCode == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE);
                latestState.setResult(resultJson("LOCAL process exited, exitCode=" + exitCode,
                        stateRequest(latestState), latestState.getLogPath(), exitCode));
                stateStore.record(latestState);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LOCAL process watcher interrupted, taskInstanceId={}", state.getTaskInstanceId(), e);
            }
        }, watcherExecutor);
    }

    private void recordControlResult(TaskRequest request, StatusEnum status, JsonNode result) {
        WorkerTaskExecutionState state = stateStore.read(request.getTaskInstanceId())
                .orElseGet(() -> baseState(request, status).build());
        state.setStatus(status);
        state.setResult(result);
        stateStore.record(state);
    }

    private WorkerTaskExecutionState.WorkerTaskExecutionStateBuilder baseState(TaskRequest request, StatusEnum status) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .pluginType(request.getPluginType())
                .runMode(ShellLocalRunModeStateMapping.RUN_MODE)
                .appId(request.getAppId())
                .status(status)
                .taskData(request.getTaskData())
                .pluginParam(request.getPluginParam());
    }

    private StatusEnum queryLocalState(TaskRequest request) {
        return stateStore.read(request.getTaskInstanceId())
                .map(WorkerTaskExecutionState::getStatus)
                .orElse(StatusEnum.UNKNOWN);
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

    private ObjectNode resultJson(String message, TaskRequest request, String agentLogPath, Integer exitCode) {
        return resultJson(message, pluginLogUri(request), agentLogPath, exitCode);
    }

    private ObjectNode resultJson(String message, String pluginLogUri, String agentLogPath, Integer exitCode) {
        return PluginResultJson.build(message, PLUGIN_TYPE, ShellLocalRunModeStateMapping.RUN_MODE, pluginLogUri,
                agentLogPath, exitCode);
    }

    private String pluginLogUri(TaskRequest request) {
        return firstText(text(request.getTaskData(), "pluginLogUri"), text(request.getPluginParam(), "pluginLogUri"));
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

    private TaskRequest stateRequest(WorkerTaskExecutionState state) {
        TaskRequest request = new TaskRequest();
        request.setTaskInstanceId(state.getTaskInstanceId());
        request.setFlowInstanceId(state.getFlowInstanceId());
        request.setPluginType(state.getPluginType());
        request.setPluginParam(state.getPluginParam());
        request.setTaskData(state.getTaskData());
        return request;
    }
}
