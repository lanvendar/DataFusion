package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.worker.model.Worker;

/**
 * 工作节点上下线事件监听器.
 *
 * @author david
 * @version 3.7.4, 2024/11/19
 * @since 3.7.4, 2024/11/19
 */
public interface WorkerListener {

    /**
     * 节点上线.
     *
     * @param worker 节点
     */
    void onActive(Worker worker);

    /**
     * 节点下线.
     *
     * @param worker 节点
     */
    void onInactive(Worker worker);
}
