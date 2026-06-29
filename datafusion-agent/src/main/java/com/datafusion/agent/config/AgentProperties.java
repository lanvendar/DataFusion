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
     * 状态刷新配置.
     */
    private StateRefresh stateRefresh = new StateRefresh();

    /**
     * Kubernetes 配置.
     */
    private Kubernetes kubernetes = new Kubernetes();

    /**
     * 插件配置.
     */
    private Plugin plugin = new Plugin();

    /**
     * 任务运行线程池配置.
     */
    private ThreadPoolConfig taskPool = new ThreadPoolConfig(8, 16, 512, 60);

    /**
     * 结果上报线程池配置.
     */
    private ThreadPoolConfig reportPool = new ThreadPoolConfig(2, 4, 512, 60);

    /**
     * worker 配置.
     */
    @Data
    public static class Worker {

        /**
         * worker 编码. 未配置时由 hostName + ip + port 推导.
         */
        private String workerCode;

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
         * worker 服务日志目录.
         */
        private String workerLogDir = "/opt/datafusion/logs/datafusion-agent";

        /**
         * worker 本地配置文件路径.
         */
        private String workerConfigPath = "/opt/datafusion-builtin/datafusion-agent/worker.config";

        /**
         * worker 可承接的插件类型，逗号分隔. 为空时使用当前 agent 已加载的全部插件.
         */
        private String pluginTypes;

        /**
         * 无法获取 hostName/ip/port 时使用的默认 worker 编码.
         */
        private String defaultWorkerCode = "00000000-0000-0000-0000-000000000001";

        /**
         * 自动获取 IP 失败时使用的默认 IP.
         */
        private String defaultIp = "127.0.0.1";

        /**
         * 自动获取主机名失败时使用的默认主机名.
         */
        private String defaultHostName = "localhost";

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
         * 任务运行态目录.
         */
        private String taskRuntimeDir = "/opt/datafusion/task-runtime";
    }

    /**
     * 状态刷新配置.
     */
    @Data
    public static class StateRefresh {

        /**
         * 状态刷新间隔，单位毫秒.
         */
        private long intervalMs = 15000L;

        /**
         * 推进 UNKNOWN 的连续查询失败阈值.
         */
        private int unknownThreshold = 3;
    }

    /**
     * Kubernetes 配置.
     */
    @Data
    public static class Kubernetes {

        /**
         * Kubernetes API 地址.
         */
        private String apiServer;

        /**
         * Bearer token.
         */
        private String token;

        /**
         * token 文件.
         */
        private String tokenFile = "/var/run/secrets/kubernetes.io/serviceaccount/token";

        /**
         * CA 证书文件.
         */
        private String caCertFile = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    }

    /**
     * 插件配置.
     */
    @Data
    public static class Plugin {

        /**
         * Spider 插件配置.
         */
        private Spider spider = new Spider();
    }

    /**
     * Spider 插件配置.
     */
    @Data
    public static class Spider {

        /**
         * 是否启用 Spider 插件入口.
         */
        private boolean enabled = false;
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
