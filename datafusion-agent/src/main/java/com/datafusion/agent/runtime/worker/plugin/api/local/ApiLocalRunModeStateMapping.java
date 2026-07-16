package com.datafusion.agent.runtime.worker.plugin.api.local;

import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalRunModeStateMapping;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import org.springframework.stereotype.Component;

/**
 * API LOCAL 运行模式状态映射.
 *
 * <p>
 * API LOCAL 通过 Shell LOCAL 启动 java 进程，状态映射复用 Shell LOCAL 规则.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/2
 * @since 1.0.0
 */
@Component
public class ApiLocalRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Shell LOCAL 状态映射.
     */
    private final ShellLocalRunModeStateMapping delegate;

    /**
     * 构造函数.
     *
     * @param delegate Shell LOCAL 状态映射
     */
    public ApiLocalRunModeStateMapping(ShellLocalRunModeStateMapping delegate) {
        this.delegate = delegate;
    }

    @Override
    public String pluginType() {
        return ApiLocalPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return ApiLocalPluginTaskExecutor.RUN_MODE;
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return delegate.mapState(snapshot, state);
    }
}
