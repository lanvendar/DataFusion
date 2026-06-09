package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;

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
     * @param request task request
     * @param param   execution param
     * @return submit result
     */
    DataxSubmitResult submit(TaskRequest request, DataxExecutionParam param);

    /**
     * Stop task.
     *
     * @param request task request
     * @param state   execution state
     * @return task result
     */
    TaskResult stop(TaskRequest request, WorkerTaskExecutionState state);

    /**
     * Kill task.
     *
     * @param request task request
     * @param state   execution state
     * @return task result
     */
    TaskResult kill(TaskRequest request, WorkerTaskExecutionState state);

    /**
     * Finish task.
     *
     * @param request task request
     * @param state   execution state
     * @return task result
     */
    TaskResult finish(TaskRequest request, WorkerTaskExecutionState state);
}
