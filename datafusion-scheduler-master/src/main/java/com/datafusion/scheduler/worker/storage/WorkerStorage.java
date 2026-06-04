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
     * 根据主机名+端口号获取工作节点.
     *
     * @param hostName 主机名
     * @param port     端口号
     * @return 工作节点信息
     */
    Worker getWorker(String hostName, int port);

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
     * 获取监控任务清单.
     *
     * @param workerId 工作节点id
     * @return 任务清单
     */
    List<TaskRequest> getTaskInsByWorkerId(String workerId);
}
