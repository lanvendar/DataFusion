package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;

/**
 * Flink task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
public interface FlinkTaskRunner {

    /**
     * Supported run mode.
     *
     * @return run mode
     */
    FlinkRunMode runMode();

    /**
     * Submit Flink task.
     * @param param execution parameter
     * @return submit result
     */
    FlinkTaskResult submit(FlinkExecutionParam param);

    /**
     * Stop Flink task.
     *
     * @param param execution parameter
     * @param state   current state
     * @return task result
     */
    FlinkTaskResult stop(FlinkExecutionParam param, WorkerTaskExecutionState state);

    /**
     * Kill Flink task.
     *
     * @param param execution parameter
     * @param state   current state
     * @return task result
     */
    FlinkTaskResult kill(FlinkExecutionParam param, WorkerTaskExecutionState state);

    /**
     * Finish Flink task.
     *
     * @param param execution parameter
     * @param state current state
     * @return true if finish completed
     */
    default boolean finish(FlinkExecutionParam param, WorkerTaskExecutionState state) {
        return true;
    }
}
