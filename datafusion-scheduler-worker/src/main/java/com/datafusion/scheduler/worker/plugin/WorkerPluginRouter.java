package com.datafusion.scheduler.worker.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Worker 插件执行器和状态映射路由器.
 *
 * <p>插件类型与运行模式共同组成路由键。重复执行器、重复状态映射，或者执行器缺少对应状态映射时，
 * 构造过程直接失败，避免任务运行后才暴露插件装配错误。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public final class WorkerPluginRouter {

    /**
     * 插件执行器映射.
     */
    private final Map<RouteKey, PluginTaskExecutor> executors = new LinkedHashMap<>();

    /**
     * 插件状态映射器映射.
     */
    private final Map<RouteKey, PluginRunModeStateMapping> stateMappings = new LinkedHashMap<>();

    /**
     * 创建 Worker 插件路由器.
     *
     * @param pluginExecutors 插件执行器集合
     * @param mappings        插件状态映射器集合
     */
    public WorkerPluginRouter(Collection<PluginTaskExecutor> pluginExecutors,
            Collection<PluginRunModeStateMapping> mappings) {
        if (mappings != null) {
            mappings.forEach(this::registerStateMapping);
        }
        if (pluginExecutors != null) {
            pluginExecutors.forEach(this::registerExecutor);
        }
        executors.keySet().forEach(key -> {
            if (!stateMappings.containsKey(key)) {
                throw new IllegalArgumentException("插件执行器缺少状态映射: " + key);
            }
        });
    }

    /**
     * 根据插件类型和运行模式获取执行器.
     *
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @return 插件执行器，未匹配时返回 {@code null}
     */
    public PluginTaskExecutor routeExecutor(String pluginType, String runMode) {
        RouteKey key = routeKey(pluginType, runMode);
        return key == null ? null : executors.get(key);
    }

    /**
     * 根据插件类型和运行模式获取状态映射器.
     *
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @return 插件状态映射器，未匹配时返回 {@code null}
     */
    public PluginRunModeStateMapping routeStateMapping(String pluginType, String runMode) {
        RouteKey key = routeKey(pluginType, runMode);
        return key == null ? null : stateMappings.get(key);
    }

    /**
     * 获取不可变插件执行器集合.
     *
     * @return 插件执行器集合
     */
    public Collection<PluginTaskExecutor> executors() {
        return Collections.unmodifiableCollection(executors.values());
    }

    /**
     * 获取已加载插件类型.
     *
     * @return 已加载插件类型
     */
    public Set<String> pluginTypes() {
        Set<String> pluginTypes = new LinkedHashSet<>();
        executors.values().forEach(executor -> pluginTypes.add(executor.pluginType()));
        return Collections.unmodifiableSet(pluginTypes);
    }

    private void registerExecutor(PluginTaskExecutor executor) {
        if (executor == null) {
            return;
        }
        RouteKey key = requiredRouteKey(executor.pluginType(), executor.runMode());
        if (executors.putIfAbsent(key, executor) != null) {
            throw new IllegalArgumentException("重复的插件执行器: " + key);
        }
    }

    private void registerStateMapping(PluginRunModeStateMapping mapping) {
        if (mapping == null) {
            return;
        }
        RouteKey key = requiredRouteKey(mapping.pluginType(), mapping.runMode());
        if (stateMappings.putIfAbsent(key, mapping) != null) {
            throw new IllegalArgumentException("重复的插件状态映射: " + key);
        }
    }

    private RouteKey routeKey(String pluginType, String runMode) {
        return isBlank(pluginType) || isBlank(runMode) ? null : new RouteKey(pluginType, runMode);
    }

    private RouteKey requiredRouteKey(String pluginType, String runMode) {
        if (isBlank(pluginType)) {
            throw new IllegalArgumentException("pluginType不能为空");
        }
        if (isBlank(runMode)) {
            throw new IllegalArgumentException("runMode不能为空");
        }
        return new RouteKey(pluginType, runMode);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 插件执行器和状态映射共同使用的路由键.
     *
     * @param pluginType 插件类型
     * @param runMode    运行模式
     * @author datafusion
     * @version 1.0.0, 2026/7/18
     * @since 1.0.0
     */
    private record RouteKey(String pluginType, String runMode) {

        private RouteKey {
            pluginType = pluginType.trim().toUpperCase(Locale.ROOT);
            runMode = runMode.trim().toUpperCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return pluginType + '/' + runMode;
        }
    }
}
