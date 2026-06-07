package com.datafusion.manager.config;

import com.datafusion.scheduler.master.MasterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 调度 master 生命周期启动器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerMasterLifecycle implements ApplicationRunner {

    /**
     * master 服务.
     */
    private final MasterService masterService;

    /**
     * 启动标记.
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void run(ApplicationArguments args) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        masterService.reloadSchedules();
        masterService.start();
    }
}
