package com.datafusion.agent.runtime.worker.plugin.spider.local;

import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalRunModeStateMapping;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import org.springframework.stereotype.Component;

/**
 * Spider LOCAL 运行模式状态映射.
 *
 * <p>
 * 第一版复用 Shell LOCAL 状态映射逻辑，仅提供独立插件注册名用于状态刷新.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/29
 * @since 1.0.0
 */
@Component
public class SpiderLocalRunModeStateMapping implements PluginRunModeStateMapping {

    /**
     * Shell LOCAL 状态映射.
     */
    private final ShellLocalRunModeStateMapping delegate;

    /**
     * 构造函数.
     *
     * @param delegate Shell LOCAL 状态映射
     */
    public SpiderLocalRunModeStateMapping(ShellLocalRunModeStateMapping delegate) {
        this.delegate = delegate;
    }

    @Override
    public String pluginType() {
        return SpiderLocalPluginTaskExecutor.PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return SpiderLocalPluginTaskExecutor.RUN_MODE;
    }

    @Override
    public StatusEnum mapState(WorkerTaskExecutionState state) {
        return delegate.mapState(state);
    }

    @Override
    public void beforeFinalReport(WorkerTaskExecutionState state) {
        delegate.beforeFinalReport(state);
    }
}
