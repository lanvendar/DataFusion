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
     * 按预期 revision 原子记录任务运行态.
     *
     * <p>当前 revision 与预期值不一致时不写入并返回 {@code false}；写入成功时 revision 自增 1。
     *
     * @param state            任务运行态
     * @param expectedRevision 预期的当前 revision，首次写入为 0
     * @return 是否写入成功
     */
    boolean saveState(WorkerTaskExecutionState state, long expectedRevision);

    /**
     * 按任务实例 ID 读取任务运行态.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务运行态
     */
    Optional<WorkerTaskExecutionState> readState(String taskInstanceId);

    /**
     * 恢复待监听任务.
     *
     * @param requests 任务请求清单
     */
    default void restoreListeningTasks(List<TaskRequest> requests) {
    }

    /**
     * 删除任务执行记录.
     *
     * @param taskInstanceId 任务实例 ID
     */
    void deleteExecution(String taskInstanceId);

}
