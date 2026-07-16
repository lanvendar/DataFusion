package com.datafusion.agent.runtime.worker.plugin.spider.local;

import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalPluginTaskExecutor;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
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
        return SpiderLocalRunModeStateMapping.RUN_MODE;
    }

    @Override
    public void validateTaskRequest(TaskRequest request) {
        delegate.validateTaskRequest(request);
    }

    @Override
    public TaskResult submitTask(TaskRequest request) {
        return delegate.submitTask(request);
    }

    @Override
    public TaskResult stopTask(TaskRequest request) {
        return delegate.stopTask(request);
    }

    @Override
    public TaskResult killTask(TaskRequest request) {
        return delegate.killTask(request);
    }

    @Override
    public boolean finishTask(TaskRequest request) {
        return delegate.finishTask(request);
    }

    @Override
    public void destroyTask(TaskRequest request) {
        delegate.destroyTask(request);
    }
}
