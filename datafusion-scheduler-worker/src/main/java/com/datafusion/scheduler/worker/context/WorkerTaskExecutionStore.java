package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.model.TaskRequest;
import java.util.List;
import java.util.Optional;

/**
 * Worker 任务执行状态存储接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
public interface WorkerTaskExecutionStore {

    /**
     * 记录任务提交快照.
     *
     * @param snapshot 任务提交快照
     */
    void saveSnapshot(WorkerTaskExecutionSnap snapshot);

    /**
     * 按任务实例 ID 读取任务提交快照.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务提交快照
     */
    Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId);

    /**
     * 记录任务运行态.
     *
     * @param state 任务运行态
     */
    void saveState(WorkerTaskExecutionState state);

    /**
     * 按任务实例 ID 读取任务运行态.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务运行态
     */
    Optional<WorkerTaskExecutionState> readState(String taskInstanceId);

    /**
     * 读取待监听任务运行态.
     *
     * @return 待监听任务运行态
     */
    List<WorkerTaskExecutionState> listListeningStates();

    /**
     * 恢复待监听任务.
     *
     * @param requests 任务请求清单
     */
    default void restoreListeningTasks(List<TaskRequest> requests) {
    }

    /**
     * 停止监听任务执行状态, 但保留本地状态文件.
     *
     * @param taskInstanceId 任务实例 ID
     */
    default void stopListening(String taskInstanceId) {
    }

    /**
     * 删除任务执行记录.
     *
     * @param taskInstanceId 任务实例 ID
     */
    void deleteExecution(String taskInstanceId);
}
