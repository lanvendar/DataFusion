package com.datafusion.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "datafusion.agent")
public class AgentProperties {

    /**
     * 模块根目录.
     */
    private String modules = System.getProperty("user.dir");

    /**
     * worker 配置.
     */
    private Worker worker = new Worker();

    /**
     * manager 配置.
     */
    private Manager manager = new Manager();

    /**
     * 存储配置.
     */
    private Storage storage = new Storage();

    /**
     * 任务控制线程池配置.
     */
    private ThreadPoolConfig taskControlPool = new ThreadPoolConfig(4, 8, 256, 60);

    /**
     * 任务运行线程池配置.
     */
    private ThreadPoolConfig taskRunPool = new ThreadPoolConfig(8, 16, 512, 60);

    /**
     * 结果上报线程池配置.
     */
    private ThreadPoolConfig resultReportPool = new ThreadPoolConfig(2, 4, 512, 60);

    /**
     * 心跳线程池配置.
     */
    private ThreadPoolConfig heartbeatPool = new ThreadPoolConfig(1, 2, 128, 60);

    /**
     * 恢复线程池配置.
     */
    private ThreadPoolConfig recoveryPool = new ThreadPoolConfig(1, 2, 128, 60);

    /**
     * worker 配置.
     */
    @Data
    public static class Worker {

        /**
         * worker ID. 未配置时由 hostName + port 推导.
         */
        private String id;

        /**
         * worker IP.
         */
        private String ip;

        /**
         * worker 端口.
         */
        private Integer port = 8081;

        /**
         * worker 主机名.
         */
        private String hostName;

        /**
         * 未注册到 manager 前是否允许接收任务.
         */
        private boolean acceptTasksBeforeRegistered = false;
    }

    /**
     * manager 配置.
     */
    @Data
    public static class Manager {

        /**
         * manager 基础地址.
         */
        private String baseUrl;

        /**
         * 是否启用 manager 注册心跳.
         */
        private boolean enabled = true;

        /**
         * 心跳间隔，单位毫秒.
         */
        private long heartbeatIntervalMs = 15000L;
    }

    /**
     * 存储配置.
     */
    @Data
    public static class Storage {

        /**
         * 日志目录名.
         */
        private String logsDir = "logs";

        /**
         * 任务状态目录名.
         */
        private String taskStatusDir = "task-status";
    }

    /**
     * 线程池配置.
     */
    @Data
    public static class ThreadPoolConfig {

        /**
         * 核心线程数.
         */
        private int corePoolSize = 1;

        /**
         * 最大线程数.
         */
        private int maxPoolSize = 2;

        /**
         * 队列容量.
         */
        private int queueCapacity = 128;

        /**
         * 线程存活时间，单位秒.
         */
        private int keepAliveSeconds = 60;

        /**
         * 构造函数.
         */
        public ThreadPoolConfig() {
        }

        /**
         * 构造函数.
         *
         * @param corePoolSize    核心线程数
         * @param maxPoolSize     最大线程数
         * @param queueCapacity   队列容量
         * @param keepAliveSeconds 线程存活时间，单位秒
         */
        public ThreadPoolConfig(int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.keepAliveSeconds = keepAliveSeconds;
        }
    }
}
