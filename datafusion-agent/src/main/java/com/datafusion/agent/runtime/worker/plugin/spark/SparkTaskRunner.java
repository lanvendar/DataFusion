package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;

/**
 * Spark 任务运行器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
public interface SparkTaskRunner {

    /**
     * 运行模式.
     *
     * @return 运行模式
     */
    SparkRunMode runMode();

    /**
     * 提交任务.
     *
     * @param param 执行参数
     * @return 提交结果
     */
    SparkTaskResult submit(SparkExecutionParam param);

    /**
     * 停止任务.
     *
     * @param param 执行参数
     * @param state 当前状态
     * @return 控制结果
     */
    SparkTaskResult stop(SparkExecutionParam param, WorkerTaskExecutionState state);

    /**
     * 强杀任务.
     *
     * @param param 执行参数
     * @param state 当前状态
     * @return 控制结果
     */
    SparkTaskResult kill(SparkExecutionParam param, WorkerTaskExecutionState state);

    /**
     * 完成后清理.
     *
     * @param param 执行参数
     * @param state 当前状态
     * @return true 表示清理完成
     */
    default boolean finish(SparkExecutionParam param, WorkerTaskExecutionState state) {
        return true;
    }
}
