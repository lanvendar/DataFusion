package com.datafusion.agent.runtime.worker.plugin.datax.local;

import com.datafusion.agent.runtime.worker.plugin.PluginResultJson;
import com.datafusion.agent.runtime.worker.plugin.template.LocalProcessSpec;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateYamlFragments;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxJobFileService;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxParamResolver;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
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
 * LOCAL DataX 插件任务执行器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Slf4j
@Component
public class LocalDataxPluginTaskExecutor extends DataxPluginTaskExecutor {

    /**
     * Job file service.
     */
    private final DataxJobFileService jobFileService;

    /**
     * Local template path.
     */
    private static final String LOCAL_TEMPLATE_PATH = "datax/templates/datax-local-runtime.yml";

    /**
     * Template renderer.
     */
    private final TemplateSpecRenderer templateRenderer;

    /**
     * Watcher executor.
     */
    private final Executor watcherExecutor;

    /** State coordinator. */
    private final WorkerTaskStateCoordinator stateCoordinator;

    /**
     * Constructor.
     *
     * @param paramResolver 参数解析器
     * @param jobFileService job file service
     * @param templateRenderer template renderer
     * @param watcherExecutor watcher executor
     * @param stateCoordinator state coordinator
     */
    public LocalDataxPluginTaskExecutor(DataxParamResolver paramResolver, DataxJobFileService jobFileService,
            TemplateSpecRenderer templateRenderer, @Qualifier("agentTaskPool") Executor watcherExecutor,
            WorkerTaskStateCoordinator stateCoordinator) {
        super(paramResolver);
        this.jobFileService = jobFileService;
        this.templateRenderer = templateRenderer;
        this.watcherExecutor = watcherExecutor;
        this.stateCoordinator = stateCoordinator;
    }

    @Override
    public String runMode() {
        return DataxRunMode.LOCAL.name();
    }

    @Override
    protected WorkerResult submit(RunningTaskContext context, DataxExecutionParam param) {
        WorkerTaskExecutionState state = context.getExecutionState();
        Process process;
        try {
            Path jobFile = jobFileService.resolveJobFile(param);
            Files.createDirectories(param.getWorkDir());
            LocalProcessSpec spec = localProcessSpec(param, jobFile);
            ProcessBuilder builder = new ProcessBuilder(spec.getCommand());
            builder.directory(param.getWorkDir().toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(param.getLogFile().toFile()));
            builder.redirectError(ProcessBuilder.Redirect.appendTo(param.getLogFile().toFile()));
            process = builder.start();
            String appId = String.valueOf(process.pid());
            WorkerTaskExecutionState actionState = state.copy();
            actionState.setAppId(appId);
            actionState.setWorkDirPath(param.getWorkDir().toString());
            watchExit(process, actionState, param.getLogFile().toString());
            applyResult(state, StatusEnum.SUBMIT_SUCCESS, appId, param.getWorkDir().toString(),
                    resultJson("LOCAL DataX task submitted", param.getLogFile().toString(), null));
        } catch (Exception e) {
            applyResult(state, StatusEnum.SUBMIT_FAILURE, null, context.getWorkDirPath(),
                    resultJson(e.getMessage(), null, null));
        }
        return workerResult(state);
    }

    @Override
    protected WorkerResult stop(RunningTaskContext context, DataxExecutionParam param) {
        return stopProcess(context, false);
    }

    @Override
    protected WorkerResult kill(RunningTaskContext context, DataxExecutionParam param) {
        return stopProcess(context, true);
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

    private WorkerResult stopProcess(RunningTaskContext context, boolean forcibly) {
        WorkerTaskExecutionState state = context.getExecutionState();
        StatusEnum targetStatus = forcibly ? StatusEnum.KILLED : StatusEnum.STOP_SUCCESS;
        ProcessHandle handle = processHandle(resolveAppId(state));
        if (handle != null) {
            if (forcibly) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        }
        applyResult(state, targetStatus, resolveAppId(state), context.getWorkDirPath(),
                resultJson(handle == null ? "LOCAL DataX process not found" : "LOCAL DataX process stopped",
                        pluginLogUri(state), null));
        return workerResult(state);
    }

    private void watchExit(Process process, WorkerTaskExecutionState actionState, String pluginLogUri) {
        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                WorkerTaskExecutionState exitState = actionState.copy();
                exitState.setExitCode(exitCode);
                exitState.setStatus(exitCode == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE);
                exitState.setResult(resultJson("LOCAL DataX process exited, exitCode=" + exitCode,
                        pluginLogUri, exitCode));
                stateCoordinator.commitLocalProcessExit(actionState, exitState);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("LOCAL DataX watcher interrupted, taskInstanceId={}", actionState.getTaskInstanceId(), e);
            } catch (RuntimeException e) {
                log.warn("LOCAL DataX watcher failed, taskInstanceId={}", actionState.getTaskInstanceId(), e);
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

    private String resolveAppId(WorkerTaskExecutionState state) {
        if (state != null && state.getAppId() != null) {
            return state.getAppId();
        }
        return null;
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private ObjectNode resultJson(String message, String pluginLogUri, Integer exitCode) {
        return PluginResultJson.build(message, "DATAX", DataxRunMode.LOCAL.name(), pluginLogUri, exitCode);
    }

    private String pluginLogUri(WorkerTaskExecutionState state) {
        if (state == null || state.getResult() == null || !state.getResult().hasNonNull("pluginLogUri")) {
            return null;
        }
        return state.getResult().get("pluginLogUri").asText();
    }

}
