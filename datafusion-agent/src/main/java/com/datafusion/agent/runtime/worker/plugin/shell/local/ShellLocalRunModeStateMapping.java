package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SHELL LOCAL 运行模式状态映射.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Component
@Slf4j
public class ShellLocalRunModeStateMapping implements PluginRunModeStateMapping {

    @Override
    public String pluginType() {
        return ShellLocalPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return ShellLocalPluginTaskExecutor.RUN_MODE;
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        if (state.getExitCode() != null) {
            return state.getExitCode() == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE;
        }
        Optional<Long> pid = parsePid(state.getAppId());
        if (pid.isEmpty()) {
            log.warn("Shell LOCAL的pid为空, taskInstanceId={}, appId={}",
                    state.getTaskInstanceId(), state.getAppId());
            return missingProcessStatus(state.getStatus());
        }
        if (ProcessHandle.of(pid.get()).map(ProcessHandle::isAlive).orElse(false)) {
            return state.getStatus() == StatusEnum.STOPPING || state.getStatus() == StatusEnum.KILLING
                    ? state.getStatus() : StatusEnum.RUNNING;
        }
        log.warn("Shell LOCAL的进程不存在, taskInstanceId={}, pid={}",
                state.getTaskInstanceId(), pid.get());
        return missingProcessStatus(state.getStatus());
    }

    private StatusEnum missingProcessStatus(StatusEnum status) {
        if (status == StatusEnum.STOPPING) {
            return StatusEnum.STOP_SUCCESS;
        }
        if (status == StatusEnum.KILLING) {
            return StatusEnum.KILLED;
        }
        return status == StatusEnum.SUBMITTING ? StatusEnum.SUBMIT_FAILURE : StatusEnum.UNKNOWN;
    }

    private Optional<Long> parsePid(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(appId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
