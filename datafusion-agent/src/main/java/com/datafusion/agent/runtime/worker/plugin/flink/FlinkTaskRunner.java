package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
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
     *
     * @param request task request
     * @param param   execution parameter
     * @return submit result
     */
    FlinkSubmitResult submit(TaskRequest request, FlinkExecutionParam param);

    /**
     * Stop Flink task.
     *
     * @param request task request
     * @param state   current state
     * @return task result
     */
    TaskResult stop(TaskRequest request, WorkerTaskExecutionState state);

    /**
     * Kill Flink task.
     *
     * @param request task request
     * @param state   current state
     * @return task result
     */
    TaskResult kill(TaskRequest request, WorkerTaskExecutionState state);
}
