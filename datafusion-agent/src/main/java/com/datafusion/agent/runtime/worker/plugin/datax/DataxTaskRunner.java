package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;

/**
 * DataX task runner.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public interface DataxTaskRunner {

    /**
     * Run mode.
     *
     * @return run mode
     */
    DataxRunMode runMode();

    /**
     * Submit task.
     *
     * @param param execution param
     * @return submit result
     */
    DataxTaskResult submit(DataxExecutionParam param);

    /**
     * Stop task.
     *
     * @param param execution param
     * @param state execution state
     * @return task result
     */
    DataxTaskResult stop(DataxExecutionParam param, WorkerTaskExecutionState state);

    /**
     * Kill task.
     *
     * @param param execution param
     * @param state execution state
     * @return task result
     */
    DataxTaskResult kill(DataxExecutionParam param, WorkerTaskExecutionState state);

    /**
     * Finish task.
     *
     * @param param execution param
     * @param state execution state
     * @return true if finished
     */
    default boolean finish(DataxExecutionParam param, WorkerTaskExecutionState state) {
        return true;
    }

}
