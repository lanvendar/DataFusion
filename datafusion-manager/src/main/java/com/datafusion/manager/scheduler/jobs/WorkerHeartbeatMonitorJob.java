package com.datafusion.manager.scheduler.jobs;

import com.datafusion.scheduler.worker.WorkerListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * worker 心跳监控定时任务.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/22
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerHeartbeatMonitorJob {

    /**
     * worker 运行时服务.
     */
    private final WorkerListener workerListener;

    /**
     * 是否启用 worker 心跳监控.
     */
    @Value("${datafusion.scheduler.worker.heartbeat-monitor.enabled:true}")
    private boolean enabled;

    /**
     * worker 心跳超时时间.
     */
    @Value("${datafusion.scheduler.worker.heartbeat-timeout-ms:300000}")
    private long heartbeatTimeoutMs;

    /**
     * 将心跳超时的在线 worker 标记为下线.
     */
    @Scheduled(fixedDelayString = "${datafusion.scheduler.worker.heartbeat-check-interval-ms:180000}")
    public void markTimeoutWorkersOffline() {
        if (!enabled) {
            return;
        }
        int updated = workerListener.timeoutOffline(heartbeatTimeoutMs);
        if (updated > 0) {
            log.info("worker heartbeat monitor marked timeout workers offline, count: {}", updated);
        }
    }
}
