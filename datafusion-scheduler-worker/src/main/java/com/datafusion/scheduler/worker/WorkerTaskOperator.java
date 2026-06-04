package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;

/**
 * Worker 侧任务操作接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public interface WorkerTaskOperator {

    /**
     * 提交任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult submitTask(TaskRequest request);

    /**
     * 停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult stopTask(TaskRequest request);

    /**
     * 强制停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult killTask(TaskRequest request);

    /**
     * 任务完成后的 worker 侧收尾动作.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    TaskResult finishTask(TaskRequest request);
}
