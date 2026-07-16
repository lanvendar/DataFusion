package com.datafusion.agent.rpc;

import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import lombok.extern.slf4j.Slf4j;

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
     * 构造函数.
     *
     * @param managerClient manager client
     */
    public ManagerTaskResultReporter(ManagerClient managerClient) {
        this.managerClient = managerClient;
    }

    @Override
    public boolean report(TaskResult result) {
        if (result == null) {
            return false;
        }
        try {
            boolean success = managerClient.reportTaskResult(result);
            if (!success) {
                log.warn("任务结果上报失败, taskInstanceId={}", result.getTaskInstanceId());
            }
            return success;
        } catch (Exception e) {
            log.warn("任务结果上报异常, taskInstanceId={}", result.getTaskInstanceId(), e);
            return false;
        }
    }
}
