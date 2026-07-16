package com.datafusion.scheduler.worker.plugin;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
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
     * 只读映射插件运行状态.
     *
     * @param snapshot 任务提交快照
     * @param state    非终态任务执行状态
     * @return 调度状态
     */
    StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state);

    /**
     * 准备插件终态上报结果.
     *
     * <p>
     * 实现只修改传入的任务执行状态，不得自行持久化。调用方根据返回值统一持久化。
     *
     * @param snapshot 任务提交快照
     * @param state    终态任务执行状态
     * @return 是否修改了任务执行状态
     */
    default boolean prepareFinalReport(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
        return false;
    }
}
