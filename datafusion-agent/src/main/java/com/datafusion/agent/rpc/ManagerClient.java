package com.datafusion.agent.rpc;

import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;

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
     * 上报任务结果.
     *
     * @param result 任务结果
     * @return 是否成功
     */
    boolean reportTaskResult(TaskResult result);
}
