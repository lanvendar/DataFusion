package com.datafusion.scheduler.worker.reporter;

import com.datafusion.scheduler.enums.StatusEnum;

/**
 * Worker 任务状态监听注册器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public interface TaskStateListenerRegistry extends TaskResultReporter {

    /**
     * 幂等注册任务状态监听.
     *
     * @param taskInstanceId 任务实例 ID
     * @param reportedStatus Manager 已接收的任务状态
     */
    void register(String taskInstanceId, StatusEnum reportedStatus);

    /**
     * 注销任务状态监听.
     *
     * @param taskInstanceId 任务实例 ID
     */
    void unregister(String taskInstanceId);

    /**
     * 关闭任务状态监听注册器.
     */
    void shutdown();
}
