package com.datafusion.scheduler.worker.plugin;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;

/**
 * 插件运行模式状态映射.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
public interface PluginRunModeStateMapping {

    /**
     * 获取插件类型.
     *
     * @return 插件类型
     */
    String pluginType();

    /**
     * 获取运行模式.
     *
     * @return 运行模式
     */
    String runMode();

    /**
     * 只读映射终端状态.
     *
     * @param state 任务执行状态
     * @return 调度状态
     */
    StatusEnum mapState(WorkerTaskExecutionState state);

    /**
     * 终态上报前执行插件侧收尾动作.
     *
     * @param state 任务执行状态
     */
    default void beforeFinalReport(WorkerTaskExecutionState state) {
    }
}
