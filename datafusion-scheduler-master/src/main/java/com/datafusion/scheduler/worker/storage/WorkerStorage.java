package com.datafusion.scheduler.worker.storage;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;

import java.util.List;

/**
 * 工作节点存储接口.
 *
 * @author david
 * @version 3.7.4, 2024/11/19
 * @since 3.7.4, 2024/11/19
 */
public interface WorkerStorage {

    /**
     * 获取工作节点.
     *
     * @param workerId worker节点主键
     * @return 工作节点
     */
    Worker getWorker(String workerId);

    /**
     * 获取全部工作节点.
     *
     * @return 工作节点列表
     */
    List<Worker> getWorkers();

    /**
     * 保存更新工作节点.
     *
     * @param worker 工作节点信息
     */
    void updateWorker(Worker worker);

    /**
     * 注册 worker.
     *
     * @param worker worker 信息
     * @return 注册后的 worker
     */
    Worker register(Worker worker);

    /**
     * worker 心跳.
     *
     * @param workerId          worker ID
     * @param lastHeartbeatTime 最近心跳时间
     * @return 心跳后的 worker
     */
    Worker heartbeat(String workerId, Long lastHeartbeatTime);

    /**
     * worker 下线.
     *
     * @param workerId worker ID
     * @return 下线后的 worker
     */
    Worker offline(String workerId);

    /**
     * 将所有在线 worker 标记为下线.
     *
     * @return 更新数量
     */
    int offlineAllWorkers();

    /**
     * 将心跳超时的 worker 标记为下线.
     *
     * @param timeoutMs 超时时间，单位毫秒
     * @return 更新数量
     */
    int timeoutOffline(Long timeoutMs);

    /**
     * 将 worker 标记为有效.
     *
     * @param workerId worker ID
     * @return 是否更新成功
     */
    boolean active(String workerId);

    /**
     * 将 worker 标记为无效.
     *
     * @param workerId worker ID
     * @return 是否更新成功
     */
    boolean inactive(String workerId);

    /**
     * 删除 worker.
     *
     * @param workerId worker ID
     * @return 是否删除成功
     */
    boolean delete(String workerId);

    /**
     * 获取监控任务清单.
     *
     * @param workerId 工作节点id
     * @return 任务清单
     */
    List<TaskRequest> getTaskInsByWorkerId(String workerId);
}
