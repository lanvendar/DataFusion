package com.datafusion.manager.asset.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 线程池控制.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/27
 * @since 2025/10/27
 */

@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * 线程池设置.
     * @return taskExecutor
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列容量
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ApiLink-");
        executor.initialize();
        return executor;
    }
}
