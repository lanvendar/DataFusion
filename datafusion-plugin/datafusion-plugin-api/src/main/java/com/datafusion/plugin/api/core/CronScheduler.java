package com.datafusion.plugin.api.core;

import com.datafusion.common.cron.CronUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.Date;

/**
 * 本地 cron 调度循环.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class CronScheduler {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CronScheduler.class);

    /**
     * 按 cron 表达式持续触发任务.
     *
     * @param cron cron 表达式
     * @param timezone 时区
     * @param task 被触发任务
     * @return 最近一次任务结果
     */
    public ApiExtractResult run(String cron, String timezone, CronTask task) {
        return run(cron, timezone, Integer.MAX_VALUE, task);
    }

    /**
     * 按 cron 表达式触发指定次数.
     *
     * @param cron cron 表达式
     * @param timezone 时区
     * @param maxRuns 最大触发次数
     * @param task 被触发任务
     * @return 最近一次任务结果
     */
    public ApiExtractResult run(String cron, String timezone, int maxRuns, CronTask task) {
        ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
        ApiExtractResult latest = null;
        int runCount = 0;
        while (!Thread.currentThread().isInterrupted() && runCount < maxRuns) {
            Date next = CronUtil.next(cron, new Date());
            long waitMs = Math.max(0, next.getTime() - System.currentTimeMillis());
            LOGGER.info("CRON 调度等待下一次触发, cron={}, timezone={}, nextTrigger={}, waitMs={}",
                    cron, timezone, next, waitMs);
            sleep(waitMs);
            LOGGER.info("CRON 调度开始触发任务, cron={}, runCount={}", cron, runCount + 1);
            latest = task.run();
            LOGGER.info("CRON 调度任务触发完成, cron={}, runCount={}, success={}, records={}, elapsedMs={}",
                    cron, runCount + 1, latest.isSuccess(), latest.getRecords(), latest.getElapsedMs());
            runCount++;
        }
        if (latest != null) {
            return latest;
        }
        throw new ApiExtractException("Cron scheduler interrupted before first run");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * cron 触发任务.
     */
    @FunctionalInterface
    public interface CronTask {
        /**
         * 执行一次任务.
         *
         * @return 任务执行结果
         */
        ApiExtractResult run();
    }
}
