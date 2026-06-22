package com.datafusion.agent.runtime.worker.plugin.datax.local;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.template.LocalProcessSpec;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateYamlFragments;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxJobFileService;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxSubmitResult;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxTaskRunner;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * LOCAL DataX task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Slf4j
@Component
public class LocalDataxTaskRunner implements DataxTaskRunner {

    /**
     * Job file service.
     */
    private final DataxJobFileService jobFileService;

    /**
     * State store.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * Local template path.
     */
    private static final String LOCAL_TEMPLATE_PATH = "plugins/datax/templates/datax-local-runtime.yml";

    /**
     * Template renderer.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * Watcher executor.
     */
    private final Executor watcherExecutor;

    /**
     * Constructor.
     *
     * @param jobFileService job file service
     * @param stateStore      state store
     * @param templateRenderer template renderer
     * @param watcherExecutor watcher executor
     */
    public LocalDataxTaskRunner(DataxJobFileService jobFileService, WorkerTaskExecutionStore stateStore,
            TemplateSpecRenderer templateRenderer, @Qualifier("agentTaskPool") Executor watcherExecutor) {
        this.jobFileService = jobFileService;
        this.stateStore = stateStore;
        this.templateRenderer = templateRenderer;
        this.watcherExecutor = watcherExecutor;
    }

    @Override
    public DataxRunMode runMode() {
        return DataxRunMode.LOCAL;
    }

    @Override
    public DataxSubmitResult submit(TaskRequest request, DataxExecutionParam param) {
        try {
            Path jobFile = jobFileService.resolveJobFile(param);
            Files.createDirectories(param.getWorkDir());
            LocalProcessSpec spec = localProcessSpec(param, jobFile);
            ProcessBuilder builder = new ProcessBuilder(spec.getCommand());
            configureProcess(param, builder);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(param.getLogFile().toFile()));
            builder.redirectError(ProcessBuilder.Redirect.appendTo(param.getLogFile().toFile()));
            Process process = builder.start();
            String appId = String.valueOf(process.pid());
            watchExit(process, request.getTaskInstanceId());
            return DataxSubmitResult.builder()
                    .status(StatusEnum.RUNNING)
                    .appId(appId)
                    .workDirPath(param.getWorkDir().toString())
                    .result(resultJson("LOCAL DataX task submitted", param.getLogFile().toString(), null, null))
                    .build();
        } catch (Exception e) {
            return DataxSubmitResult.builder()
                    .status(StatusEnum.SUBMIT_FAILURE)
                    .result(resultJson(e.getMessage(), null, null, null))
                    .build();
        }
    }

    @Override
    public TaskResult stop(TaskRequest request, WorkerTaskExecutionState state) {
        return stopProcess(request, state, false);
    }

    @Override
    public TaskResult kill(TaskRequest request, WorkerTaskExecutionState state) {
        return stopProcess(request, state, true);
    }

    @Override
    public TaskResult finish(TaskRequest request, WorkerTaskExecutionState state) {
        StatusEnum status = state == null ? StatusEnum.UNKNOWN : state.getStatus();
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(status == null ? StatusEnum.UNKNOWN : status)
                .appId(request.getAppId())
                .workDirPath(state == null ? null : state.getWorkDirPath())
                .result(resultJson("LOCAL DataX finish checked", pluginLogUri(state), null, null))
                .build();
    }

    private LocalProcessSpec localProcessSpec(DataxExecutionParam param, Path jobFile) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("javaBin", param.getJavaBin()),
                Map.entry("jvmOptions", TemplateYamlFragments.listItems(param.getJvmOptions(), 2)),
                Map.entry("dataxHome", required(param.getDataxHome(), "dataxHome不能为空")),
                Map.entry("logLevel", param.getLogLevel()),
                Map.entry("logFile", param.getLogFile().toString()),
                Map.entry("logMaxSize", param.getLogMaxSize()),
                Map.entry("logMaxIndex", String.valueOf(param.getLogMaxIndex())),
                Map.entry("logConfigFile", required(param.getLogbackConfigFile(), "logConfigFile不能为空")),
                Map.entry("dataxJar", required(param.getDataxJar(), "dataxJar不能为空")),
                Map.entry("mainClass", required(param.getMainClass(), "mainClass不能为空")),
                Map.entry("jobMode", required(param.getJobMode(), "jobMode不能为空")),
                Map.entry("jobId", required(param.getJobId(), "jobId不能为空")),
                Map.entry("jobFile", jobFile.toString())
        );
        LocalProcessSpec spec = templateRenderer.renderYaml(LOCAL_TEMPLATE_PATH, values, LocalProcessSpec.class);
        if (spec.getCommand() == null || spec.getCommand().isEmpty()) {
            throw new IllegalArgumentException("DATAX LOCAL command不能为空");
        }
        return spec;
    }

    private void configureProcess(DataxExecutionParam param, ProcessBuilder builder) {
        builder.directory(param.getWorkDir().toFile());
    }

    private TaskResult stopProcess(TaskRequest request, WorkerTaskExecutionState state, boolean forcibly) {
        StatusEnum targetStatus = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        ProcessHandle handle = processHandle(resolveAppId(request, state));
        if (handle != null) {
            if (forcibly) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        }
        return TaskResult.builder()
                .taskInstanceId(request.getTaskInstanceId())
                .flowInstanceId(request.getFlowInstanceId())
                .taskName(request.getTaskName())
                .taskState(targetStatus)
                .appId(resolveAppId(request, state))
                .workDirPath(state == null ? null : state.getWorkDirPath())
                .result(resultJson(handle == null ? "LOCAL DataX process not found" : "LOCAL DataX process stopped",
                        pluginLogUri(state), null, null))
                .build();
    }

    private void watchExit(Process process, String taskInstanceId) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                WorkerTaskExecutionState state = stateStore.readState(taskInstanceId).orElse(null);
                if (state == null || state.getStatus() != null && state.getStatus().isFinalState()) {
                    return;
                }
                state.setExitCode(exitCode);
                state.setStatus(exitCode == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE);
                state.setResult(resultJson("LOCAL DataX process exited, exitCode=" + exitCode, pluginLogUri(state),
                        null, exitCode));
                stateStore.saveState(state);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LOCAL DataX watcher interrupted, taskInstanceId={}", taskInstanceId, e);
            }
        }, watcherExecutor);
    }

    private ProcessHandle processHandle(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return null;
        }
        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(Long.parseLong(appId.trim()));
            return handle.orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveAppId(TaskRequest request, WorkerTaskExecutionState state) {
        if (state != null && state.getAppId() != null) {
            return state.getAppId();
        }
        return request.getAppId();
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private ObjectNode resultJson(String message, String pluginLogUri, String agentLogPath, Integer exitCode) {
        return PluginResultJson.build(message, "DATAX", DataxRunMode.LOCAL.name(), pluginLogUri, agentLogPath,
                exitCode);
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
    }
}
