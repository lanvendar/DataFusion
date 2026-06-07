package com.datafusion.agent.runtime.worker.plugin.shell;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * 任务执行状态存储.
     */
    private final WorkerTaskExecutionStateStore stateStore;

    /**
     * 构造函数.
     *
     * @param properties agent 配置
     * @param stateStore 任务执行状态存储
     */
    public ShellLocalPluginTaskExecutor(AgentProperties properties, WorkerTaskExecutionStateStore stateStore) {
        this.properties = properties;
        this.stateStore = stateStore;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public TaskRequest prepareTask(TaskRequest request) {
        command(request);
        return request;
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command(request));
            configureProcess(request, processBuilder);
            Path logDir = logDir(request);
            Files.createDirectories(logDir);
            processBuilder.redirectOutput(logDir.resolve("stdout.log").toFile());
            processBuilder.redirectError(logDir.resolve("stderr.log").toFile());
            Process process = processBuilder.start();
            String appId = String.valueOf(process.pid());
            WorkerTaskExecutionState state = baseState(request, StatusEnum.RUNNING)
                    .appId(appId)
                    .result("LOCAL shell task submitted")
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
                    .result("LOCAL shell task submitted")
                    .build();
        } catch (Exception e) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.SUBMIT_FAILURE)
                    .result(e.getMessage())
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
                    .result("terminal task is not finished")
                    .build();
        }
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(state == null ? StatusEnum.UNKNOWN : state)
                .appId(request.getAppId())
                .result("finish checked")
                .build();
    }

    private TaskResult stopProcess(TaskRequest request, boolean forcibly) {
        StatusEnum targetStatus = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        ProcessHandle handle = processHandle(request.getAppId());
        if (handle == null) {
            recordControlResult(request, targetStatus, "process not found");
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(targetStatus)
                    .appId(request.getAppId())
                    .result("process not found")
                    .build();
        }
        if (forcibly) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
        }
        recordControlResult(request, targetStatus, forcibly ? "process killed" : "process stopped");
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(targetStatus)
                .appId(request.getAppId())
                .result(forcibly ? "process killed" : "process stopped")
                .build();
    }

    private void configureProcess(TaskRequest request, ProcessBuilder processBuilder) {
        JsonNode shellParam = shellParam(request);
        if (shellParam == null) {
            return;
        }
        if (shellParam.hasNonNull("workDir")) {
            processBuilder.directory(Path.of(shellParam.get("workDir").asText()).toFile());
        }
        JsonNode envNode = shellParam.get("env");
        if (envNode != null && envNode.isObject()) {
            for (Map.Entry<String, JsonNode> entry : envNode.properties()) {
                processBuilder.environment().put(entry.getKey(), entry.getValue().asText());
            }
        }
    }

    private List<String> command(TaskRequest request) {
        JsonNode shellParam = shellParam(request);
        if (shellParam == null || !shellParam.hasNonNull("command")) {
            throw new IllegalArgumentException("pluginParam.command不能为空");
        }
        List<String> command = new ArrayList<>();
        command.add(shellParam.get("command").asText());
        JsonNode argsNode = shellParam.get("args");
        if (argsNode != null && argsNode.isArray()) {
            argsNode.forEach(arg -> command.add(arg.asText()));
        }
        return command;
    }

    private JsonNode shellParam(TaskRequest request) {
        JsonNode pluginParam = request.getPluginParam();
        if (pluginParam != null && pluginParam.hasNonNull("command")) {
            return pluginParam;
        }
        return request.getTaskData();
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
                latestState.setResult("LOCAL process exited, exitCode=" + exitCode);
                stateStore.record(latestState);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LOCAL process watcher interrupted, taskInstanceId={}", state.getTaskInstanceId(), e);
            }
        });
    }

    private void recordControlResult(TaskRequest request, StatusEnum status, String result) {
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
}
