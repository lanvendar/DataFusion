package com.datafusion.agent.rpc;

import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 上报任务结果到 manager.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Slf4j
public class ManagerTaskResultReporter implements TaskResultReporter {

    /**
     * manager client.
     */
    private final ManagerClient managerClient;

    /**
     * 上报线程池.
     */
    private final ThreadPoolExecutor reportPool;

    /**
     * 构造函数.
     *
     * @param managerClient manager client
     * @param reportPool    上报线程池
     */
    public ManagerTaskResultReporter(ManagerClient managerClient, ThreadPoolExecutor reportPool) {
        this.managerClient = managerClient;
        this.reportPool = reportPool;
    }

    @Override
    public boolean report(TaskResult result) {
        if (result == null) {
            return false;
        }
        try {
            reportPool.execute(() -> doReport(result));
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("结果上报线程池已满, taskInstanceId={}", result.getTaskInstanceId(), e);
            return false;
        }
    }

    private void doReport(TaskResult result) {
        boolean success = managerClient.reportTaskResult(result);
        if (!success) {
            log.warn("任务结果上报失败, taskInstanceId={}", result.getTaskInstanceId());
        }
    }
}
