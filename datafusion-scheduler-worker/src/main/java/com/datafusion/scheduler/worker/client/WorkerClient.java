package com.datafusion.scheduler.worker.client;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;

import java.util.List;
import java.util.Optional;

/**
 * Worker 与 Manager 通信客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public interface WorkerClient {

    /**
     * 注册 Worker.
     *
     * @param worker Worker 信息
     * @return Manager 保存后的 Worker 信息
     */
    Worker register(Worker worker);

    /**
     * 上报 Worker 心跳.
     *
     * @param worker Worker 信息
     * @return Manager 保存后的 Worker 信息
     */
    Worker heartbeat(Worker worker);

    /**
     * Worker 下线.
     *
     * @param worker Worker 信息
     * @return Manager 保存后的 Worker 信息
     */
    Worker offline(Worker worker);

    /**
     * 查询 Worker 的未完成任务.
     *
     * <p>{@link Optional#empty()} 表示查询失败，包含空集合的 Optional 表示查询成功且没有未完成任务。
     *
     * @param worker Worker 信息
     * @return 未完成任务查询结果
     */
    Optional<List<TaskRequest>> findUnfinishedTasks(Worker worker);
}
