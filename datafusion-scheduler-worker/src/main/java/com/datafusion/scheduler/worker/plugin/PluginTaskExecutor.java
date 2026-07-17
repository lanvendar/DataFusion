package com.datafusion.scheduler.worker.plugin;

import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;

/**
 * Worker 插件任务执行器.
 *
 * <p>执行器只负责校验参数和调用本地或第三方运行时。执行状态由调用方通过上下文候选副本和状态协调器统一提交，
 * 插件不得直接读写任务执行存储。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public interface PluginTaskExecutor {

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
     * 校验任务动作上下文.
     *
     * @param context 任务动作上下文
     */
    default void validate(RunningTaskContext context) {
    }

    /**
     * 提交任务.
     *
     * @param context 任务动作上下文
     * @return Worker 执行结果
     */
    WorkerResult submit(RunningTaskContext context);

    /**
     * 停止任务.
     *
     * @param context 任务动作上下文
     * @return Worker 执行结果
     */
    WorkerResult stop(RunningTaskContext context);

    /**
     * 强制停止任务.
     *
     * @param context 任务动作上下文
     * @return Worker 执行结果
     */
    WorkerResult kill(RunningTaskContext context);

    /**
     * 完成插件侧任务清理.
     *
     * @param context 任务动作上下文
     * @return 是否完成清理
     */
    boolean finish(RunningTaskContext context);
}
