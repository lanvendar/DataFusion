package com.datafusion.scheduler.worker.plugin;

import java.util.List;

/**
 * Worker 插件加载接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public interface WorkerPluginLoader {

    /**
     * 加载插件任务执行器.
     *
     * @return 插件任务执行器列表
     */
    List<PluginTaskExecutor> loadPlugins();
}
