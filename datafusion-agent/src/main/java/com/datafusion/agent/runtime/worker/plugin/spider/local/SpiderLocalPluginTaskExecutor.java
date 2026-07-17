package com.datafusion.agent.runtime.worker.plugin.spider.local;

import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalPluginTaskExecutor;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Spider 本地插件执行器.
 *
 * <p>
 * 第一版复用 Shell LOCAL 执行逻辑，仅提供独立插件注册名用于调度分流.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/29
 * @since 1.0.0
 */
@Component
public class SpiderLocalPluginTaskExecutor implements PluginTaskExecutor {

    /**
     * 插件类型.
     */
    public static final String PLUGIN_TYPE = "SPIDER";

    /**
     * 运行模式.
     */
    public static final String RUN_MODE = "LOCAL";

    /**
     * Shell LOCAL 执行器.
     */
    private final ShellLocalPluginTaskExecutor delegate;

    /**
     * 构造函数.
     *
     * @param delegate Shell LOCAL 执行器
     */
    public SpiderLocalPluginTaskExecutor(ShellLocalPluginTaskExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public String pluginType() {
        return PLUGIN_TYPE;
    }

    @Override
    public String runMode() {
        return RUN_MODE;
    }

    @Override
    public void validate(RunningTaskContext context) {
        delegate.validate(context);
    }

    @Override
    public WorkerResult submit(RunningTaskContext context) {
        return delegate.submit(context);
    }

    @Override
    public WorkerResult stop(RunningTaskContext context) {
        return delegate.stop(context);
    }

    @Override
    public WorkerResult kill(RunningTaskContext context) {
        return delegate.kill(context);
    }

    @Override
    public boolean finish(RunningTaskContext context) {
        return delegate.finish(context);
    }
}
