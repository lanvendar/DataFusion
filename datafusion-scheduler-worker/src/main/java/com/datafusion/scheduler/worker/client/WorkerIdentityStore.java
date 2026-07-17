package com.datafusion.scheduler.worker.client;

import com.datafusion.scheduler.model.Worker;

import java.util.Optional;

/**
 * Worker 本地身份存储.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public interface WorkerIdentityStore {

    /**
     * 加载本地 Worker 身份.
     *
     * @return 本地 Worker 身份
     */
    Optional<Worker> load();

    /**
     * 保存本地 Worker 身份.
     *
     * @param worker Worker 身份
     */
    void save(Worker worker);

    /**
     * 删除本地 Worker 身份.
     */
    default void delete() {
    }
}
