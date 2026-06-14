package com.datafusion.agent.runtime.worker.plugin.shell.local;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
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
public class ShellLocalRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * 运行模式.
     */
    public static final String RUN_MODE = "LOCAL";

    @Override
    public String pluginType() {
        return ShellLocalPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return RUN_MODE;
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionState state) {
        if (state == null) {
            return StatusEnum.UNKNOWN;
        }
        StatusEnum currentStatus = state.getStatus();
        if (currentStatus != null && currentStatus.isFinalState()) {
            return currentStatus;
        }
        if (state.getExitCode() != null) {
            return state.getExitCode() == 0 ? StatusEnum.RUN_SUCCESS : StatusEnum.RUN_FAILURE;
        }
        Optional<Long> pid = parsePid(state.getAppId());
        if (pid.isEmpty()) {
            return StatusEnum.UNKNOWN;
        }
        return ProcessHandle.of(pid.get()).map(ProcessHandle::isAlive).orElse(false)
                ? StatusEnum.RUNNING : StatusEnum.UNKNOWN;
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
