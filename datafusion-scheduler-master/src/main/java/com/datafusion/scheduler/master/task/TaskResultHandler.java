package com.datafusion.scheduler.master.task;

import com.datafusion.scheduler.model.TaskResult;

/**
 * 接收 worker端返回结果的异步接口 .
 *
 * <p>
 * 对应 worker端 TaskResultReporter 接口
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/5
 * @since 2024/12/5
 */
public interface TaskResultHandler {
    /**
     * 上报任务状态.
     *
     * @param result 任务状态
     * @return 是否成功
     */
    boolean asyncHandle(TaskResult result);
}
