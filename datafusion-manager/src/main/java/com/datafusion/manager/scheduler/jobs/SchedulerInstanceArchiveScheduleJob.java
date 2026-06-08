package com.datafusion.manager.scheduler.jobs;

import com.datafusion.manager.scheduler.service.SchedulerInstanceArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 调度-实例归档定时任务.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerInstanceArchiveScheduleJob {

    /**
     * 实例归档Service.
     */
    private final SchedulerInstanceArchiveService schedulerInstanceArchiveService;

    /**
     * 是否启用实例归档.
     */
    @Value("${datafusion.scheduler.instance.archive.enabled:true}")
    private boolean enabled;

    /**
     * 归档批次大小.
     */
    @Value("${datafusion.scheduler.instance.archive.batch-size:200}")
    private int batchSize;

    /**
     * 归档成功实例.
     */
    @Scheduled(cron = "${datafusion.scheduler.instance.archive.cron:0 0/10 * * * ?}")
    public void archiveSuccessInstances() {
        if (!enabled) {
            return;
        }

        int archived = schedulerInstanceArchiveService.archiveSuccessInstances(batchSize);
        if (archived > 0) {
            log.info("scheduler instance archive finished, archived count: {}", archived);
        }
    }
}
