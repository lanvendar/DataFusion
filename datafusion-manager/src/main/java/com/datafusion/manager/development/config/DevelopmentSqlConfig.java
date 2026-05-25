package com.datafusion.manager.development.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 开发侧SQL异步执行的线程池配置.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Configuration
public class DevelopmentSqlConfig {

    /**
     * 开发侧SQL异步执行线程池Bean名称.
     */
    public static final String DEV_SQL_EXECUTOR = "developmentSqlExecutor";

    /**
     * 开发侧SQL执行专用线程池.
     *
     * @return 线程池
     */
    @Bean(name = DEV_SQL_EXECUTOR)
    public Executor developmentSqlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("dev-sql-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
