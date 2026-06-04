package com.datafusion.scheduler.worker.reporter;

import com.datafusion.scheduler.model.TaskResult;

/**
 * 空任务结果上报实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public class NoopTaskResultReporter implements TaskResultReporter {

    @Override
    public boolean report(TaskResult result) {
        return true;
    }
}
