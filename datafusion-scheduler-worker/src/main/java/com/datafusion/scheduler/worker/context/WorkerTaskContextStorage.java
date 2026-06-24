package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.model.TaskRequest;

/**
 * Worker 运行中任务上下文存储接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public interface WorkerTaskContextStorage {

    /**
     * 获取上下文.
     *
     * @param taskInstanceId 任务实例ID
     * @return 运行中任务上下文
     */
    RunningTaskContext get(String taskInstanceId);

    /**
     * 获取已有上下文，不存在时创建.
     *
     * @param request 任务请求
     * @return 运行中任务上下文
     */
    RunningTaskContext getOrCreate(TaskRequest request);

    /**
     * 保存上下文.
     *
     * @param context 运行中任务上下文
     */
    void save(RunningTaskContext context);

    /**
     * 移除当前运行上下文.
     *
     * @param taskInstanceId 任务实例ID
     */
    void removeContext(String taskInstanceId);
}
