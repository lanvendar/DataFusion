package com.datafusion.agent.runtime.worker.plugin.datax.local;

import com.datafusion.agent.runtime.worker.plugin.datax.DataxPluginTaskExecutor;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxRunMode;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * DataX LOCAL 运行模式状态映射.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Component
@Slf4j
public class DataxLocalRunModeStateMapping implements PluginRunModeStateMapping {

    @Override
    public String pluginType() {
        return DataxPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return DataxRunMode.LOCAL.name();
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionState state) {
        if (state == null) {
            log.warn("DataX LOCAL的状态为空, taskState=null");
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
            log.warn("DataX LOCAL的pid为空, taskInstanceId={}, appId={}",
                    state.getTaskInstanceId(), state.getAppId());
            return StatusEnum.UNKNOWN;
        }
        if (ProcessHandle.of(pid.get()).map(ProcessHandle::isAlive).orElse(false)) {
            return StatusEnum.RUNNING;
        }
        log.warn("DataX LOCAL的进程不存在, taskInstanceId={}, pid={}",
                state.getTaskInstanceId(), pid.get());
        return StatusEnum.UNKNOWN;
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
