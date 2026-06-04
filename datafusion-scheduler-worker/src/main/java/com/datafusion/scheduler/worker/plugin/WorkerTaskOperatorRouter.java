package com.datafusion.scheduler.worker.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Worker 插件路由器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public class WorkerTaskOperatorRouter {

    /**
     * 插件执行器映射.
     */
    private final Map<String, PluginTaskExecutor> executorMap = new LinkedHashMap<>();

    /**
     * 构造函数.
     *
     * @param executors 插件执行器集合
     */
    public WorkerTaskOperatorRouter(Collection<PluginTaskExecutor> executors) {
        if (executors != null) {
            executors.forEach(this::register);
        }
    }

    /**
     * 从插件加载器创建路由器.
     *
     * @param loader 插件加载器
     * @return 插件路由器
     */
    public static WorkerTaskOperatorRouter fromLoader(WorkerPluginLoader loader) {
        if (loader == null) {
            return new WorkerTaskOperatorRouter(Collections.emptyList());
        }
        return new WorkerTaskOperatorRouter(loader.loadPlugins());
    }

    /**
     * 注册插件执行器.
     *
     * @param executor 插件执行器
     */
    public final void register(PluginTaskExecutor executor) {
        if (executor == null) {
            return;
        }
        String pluginType = executor.pluginType();
        if (pluginType == null || pluginType.trim().isEmpty()) {
            throw new IllegalArgumentException("pluginType不能为空");
        }
        if (executorMap.containsKey(pluginType)) {
            throw new IllegalArgumentException("重复的pluginType: " + pluginType);
        }
        executorMap.put(pluginType, executor);
    }

    /**
     * 根据插件类型获取执行器.
     *
     * @param pluginType 插件类型
     * @return 插件执行器
     */
    public PluginTaskExecutor route(String pluginType) {
        if (pluginType == null) {
            return null;
        }
        return executorMap.get(pluginType);
    }

    /**
     * 获取插件执行器映射.
     *
     * @return 不可变映射
     */
    public Map<String, PluginTaskExecutor> executors() {
        return Collections.unmodifiableMap(executorMap);
    }
}
