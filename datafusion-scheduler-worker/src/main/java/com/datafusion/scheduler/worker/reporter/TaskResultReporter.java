package com.datafusion.scheduler.worker.reporter;

import com.datafusion.scheduler.model.TaskResult;

/**
 * 任务结果上报接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public interface TaskResultReporter {

    /**
     * 上报任务结果.
     *
     * @param result 任务结果
     * @return 是否成功
     */
    boolean report(TaskResult result);
}
