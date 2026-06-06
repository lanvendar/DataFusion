package com.datafusion.scheduler.worker.state;

import java.util.List;
import java.util.Optional;

/**
 * Worker 任务执行状态存储接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
public interface WorkerTaskExecutionStateStore {

    /**
     * 记录任务执行状态.
     *
     * @param state 任务执行状态
     */
    void record(WorkerTaskExecutionState state);

    /**
     * 按任务实例 ID 读取任务执行状态.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务执行状态
     */
    Optional<WorkerTaskExecutionState> read(String taskInstanceId);

    /**
     * 读取已记录任务执行状态.
     *
     * @return 已记录任务执行状态
     */
    List<WorkerTaskExecutionState> listRecords();

    /**
     * 删除任务执行状态.
     *
     * @param taskInstanceId 任务实例 ID
     */
    void remove(String taskInstanceId);
}
