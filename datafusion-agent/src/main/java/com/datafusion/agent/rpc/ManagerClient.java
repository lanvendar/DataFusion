package com.datafusion.agent.rpc;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;

import java.util.List;
import java.util.Optional;

/**
 * Manager 通信客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public interface ManagerClient {

    /**
     * 注册 worker.
     *
     * @param worker worker 信息
     * @return manager 保存后的 worker 信息
     */
    Worker register(Worker worker);

    /**
     * worker 心跳.
     *
     * @param worker worker 信息
     * @return manager 保存后的 worker 信息
     */
    Worker heartbeat(Worker worker);

    /**
     * worker 下线.
     *
     * @param worker worker 信息
     * @return manager 保存后的 worker 信息
     */
    Worker offline(Worker worker);

    /**
     * 查询 worker 未完成任务清单.
     *
     * @param worker worker 信息
     * @return 未完成任务清单，调用失败时为空
     */
    Optional<List<TaskRequest>> getTaskInsByWorker(Worker worker);

    /**
     * 上报任务结果.
     *
     * @param result 任务结果
     * @return 是否成功
     */
    boolean reportTaskResult(TaskResult result);
}
