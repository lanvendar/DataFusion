package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;

import java.util.List;

/**
 * 工作节点上下线事件监听器.
 *
 * @author david
 * @version 3.7.4, 2024/11/19
 * @since 3.7.4, 2024/11/19
 */
public interface WorkerListener {

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
     * 将心跳超时的 worker 标记为下线.
     *
     * @param timeoutMs 超时时间，单位毫秒
     * @return 更新数量
     */
    int timeoutOffline(Long timeoutMs);

    /**
     * 获取指定 worker 的未完成任务清单.
     *
     * @param workerId worker ID
     * @return 未完成任务清单
     */
    List<TaskRequest> getTaskInsByWorkerId(String workerId);

    /**
     * 获取指定 worker.
     *
     * @param workerId worker ID
     * @return worker 信息
     */
    Worker getWorker(String workerId);

    /**
     * 选择支持指定插件的可用 worker.
     *
     * @param pluginType 插件类型
     * @return worker 信息
     */
    Worker lookupWorker(String pluginType);
}
