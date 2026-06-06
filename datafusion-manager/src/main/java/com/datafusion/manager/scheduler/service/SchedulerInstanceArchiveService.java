package com.datafusion.manager.scheduler.service;

/**
 * 调度-实例归档Service.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
public interface SchedulerInstanceArchiveService {

    /**
     * 归档成功实例.
     *
     * @param batchSize 批次大小
     * @return 归档记录数
     */
    int archiveSuccessInstances(int batchSize);
}
