package com.datafusion.scheduler.worker.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final Map<RouteKey, PluginTaskExecutor> executorMap = new LinkedHashMap<>();

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
        String runMode = executor.runMode();
        if (runMode == null || runMode.trim().isEmpty()) {
            throw new IllegalArgumentException("runMode不能为空");
        }
        RouteKey routeKey = new RouteKey(pluginType, runMode);
        if (executorMap.containsKey(routeKey)) {
            throw new IllegalArgumentException("重复的插件执行器: " + pluginType + "/" + runMode);
        }
        executorMap.put(routeKey, executor);
    }

    /**
     * 根据插件类型和运行模式获取执行器.
     *
     * @param pluginType 插件类型
     * @param runMode 运行模式
     * @return 插件执行器
     */
    public PluginTaskExecutor route(String pluginType, String runMode) {
        if (pluginType == null || runMode == null) {
            return null;
        }
        return executorMap.get(new RouteKey(pluginType, runMode));
    }

    /**
     * 获取插件执行器集合.
     *
     * @return 不可变集合
     */
    public Collection<PluginTaskExecutor> executors() {
        return Collections.unmodifiableCollection(executorMap.values());
    }

    /**
     * 获取已加载插件类型.
     *
     * @return 插件类型集合
     */
    public Set<String> pluginTypes() {
        Set<String> pluginTypes = new LinkedHashSet<>();
        executorMap.values().forEach(executor -> pluginTypes.add(executor.pluginType()));
        return Collections.unmodifiableSet(pluginTypes);
    }

    /**
     * 插件执行器路由键.
     *
     * @param pluginType 插件类型
     * @param runMode 运行模式
     */
    private record RouteKey(String pluginType, String runMode) {

        private RouteKey {
            pluginType = pluginType.trim().toUpperCase(Locale.ROOT);
            runMode = runMode.trim().toUpperCase(Locale.ROOT);
        }
    }
}
