package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.model.Worker;

/**
 * 工作节点人工操作接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/23
 * @since 1.0.0
 */
public interface WorkerOperator {

    /**
     * 将 worker 标记为有效.
     *
     * @param workerId worker ID
     * @return 更新后的 worker
     */
    Worker active(String workerId);

    /**
     * 将 worker 标记为无效.
     *
     * @param workerId worker ID
     * @return 更新后的 worker
     */
    Worker inactive(String workerId);

    /**
     * 删除 worker.
     *
     * @param workerId worker ID
     * @return 是否删除成功
     */
    boolean delete(String workerId);
}
