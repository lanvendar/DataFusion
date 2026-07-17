package com.datafusion.agent.config;

import com.datafusion.agent.rpc.AgentTaskResultReporter;
import com.datafusion.agent.rpc.AgentWorkerClient;
import com.datafusion.agent.runtime.AgentWorkerConfigStore;
import com.datafusion.agent.runtime.worker.context.FileWorkerTaskExecutionStore;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateListenerRegistry;
import com.datafusion.common.threadpool.NamedThreadFactory;
import com.datafusion.common.threadpool.ThreadPoolBuilder;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.worker.WorkerService;
import com.datafusion.scheduler.worker.client.WorkerClient;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerPluginRouter;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.reporter.TaskStateListenerRegistry;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 运行时装配.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Configuration
public class AgentConfiguration {

    /**
     * 创建任务动作和本地进程 watcher 共用线程池.
     *
     * @param properties Agent 配置
     * @return 任务线程池
     */
    @Bean(name = "agentTaskPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentTaskPool(AgentProperties properties) {
        AgentProperties.ThreadPoolConfig config = properties.getTaskPool();
        return ThreadPoolBuilder.create()
                .setCorePoolSize(config.getCorePoolSize())
                .setMaxPoolSize(config.getMaxPoolSize())
                .setQueueCapacity(config.getQueueCapacity())
                .setKeepAliveSeconds(config.getKeepAliveSeconds())
                .setPoolName("agent-task")
                .build();
    }

    /**
     * 创建 Worker 心跳调度器.
     *
     * @return 心跳调度器
     */
    @Bean(name = "agentHeartbeatScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService agentHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("agent-heartbeat-scheduler"));
    }

    /**
     * 创建任务状态监听调度器.
     *
     * @param properties Agent 配置
     * @return 状态监听调度器
     */
    @Bean(name = "agentStateRefreshScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService agentStateRefreshScheduler(AgentProperties properties) {
        int poolSize = Math.max(properties.getStateRefresh().getListenerPoolSize(), 1);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(poolSize,
                new NamedThreadFactory("agent-task-state-listener"));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * 创建普通 Manager RestTemplate.
     *
     * @param properties Agent 配置
     * @return HTTP 客户端
     */
    @Bean("agentManagerRestTemplate")
    @ConditionalOnProperty(prefix = "spring.cloud.nacos.discovery", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    public RestTemplate agentManagerRestTemplate(AgentProperties properties) {
        return managerRestTemplate(properties);
    }

    /**
     * 创建负载均衡 Manager RestTemplate.
     *
     * @param properties Agent 配置
     * @return HTTP 客户端
     */
    @Bean("agentManagerRestTemplate")
    @LoadBalanced
    @ConditionalOnProperty(prefix = "spring.cloud.nacos.discovery", name = "enabled", havingValue = "true")
    public RestTemplate loadBalancedAgentManagerRestTemplate(AgentProperties properties) {
        return managerRestTemplate(properties);
    }

    /**
     * 创建 Worker 协议客户端.
     *
     * @param restTemplate HTTP 客户端
     * @param properties   Agent 配置
     * @return Worker 协议客户端
     */
    @Bean
    public WorkerClient workerClient(@Qualifier("agentManagerRestTemplate") RestTemplate restTemplate,
            AgentProperties properties) {
        return new AgentWorkerClient(restTemplate, properties);
    }

    /**
     * 创建单次任务结果上报器.
     *
     * @param restTemplate HTTP 客户端
     * @param properties   Agent 配置
     * @return 任务结果上报器
     */
    @Bean("agentTaskResultReporter")
    public TaskResultReporter taskResultReporter(
            @Qualifier("agentManagerRestTemplate") RestTemplate restTemplate, AgentProperties properties) {
        return new AgentTaskResultReporter(restTemplate, properties);
    }

    /**
     * 创建插件执行器和状态映射统一路由器.
     *
     * @param executors 插件执行器
     * @param mappings  状态映射器
     * @return 插件路由器
     */
    @Bean
    public WorkerPluginRouter workerPluginRouter(List<PluginTaskExecutor> executors,
            List<PluginRunModeStateMapping> mappings) {
        return new WorkerPluginRouter(executors, mappings);
    }

    /**
     * 创建文件任务执行存储.
     *
     * @param properties Agent 配置
     * @return 任务执行存储
     */
    @Bean
    public WorkerTaskExecutionStore workerTaskExecutionStore(AgentProperties properties) {
        return new FileWorkerTaskExecutionStore(properties);
    }

    /**
     * 创建任务状态协调器.
     *
     * @param executionStore 任务执行存储
     * @return 状态协调器
     */
    @Bean
    public WorkerTaskStateCoordinator workerTaskStateCoordinator(WorkerTaskExecutionStore executionStore) {
        return new WorkerTaskStateCoordinator(executionStore);
    }

    /**
     * 创建任务状态监听注册器.
     *
     * @param executionStore  任务执行存储
     * @param coordinator     状态协调器
     * @param pluginRouter    插件路由器
     * @param resultReporter  单次结果上报器
     * @param scheduler       状态监听调度器
     * @param properties      Agent 配置
     * @return 任务监听注册器
     */
    @Bean
    public TaskStateListenerRegistry taskStateListenerRegistry(WorkerTaskExecutionStore executionStore,
            WorkerTaskStateCoordinator coordinator, WorkerPluginRouter pluginRouter,
            @Qualifier("agentTaskResultReporter") TaskResultReporter resultReporter,
            @Qualifier("agentStateRefreshScheduler") ScheduledExecutorService scheduler,
            AgentProperties properties) {
        AgentProperties.StateRefresh config = properties.getStateRefresh();
        return new AgentTaskStateListenerRegistry(executionStore, coordinator, pluginRouter,
                resultReporter, scheduler, config.getIntervalMs(), config.getUnknownThreshold(),
                config.getListenerRetentionMs(), config.getListenerRetentionNum());
    }

    /**
     * 创建 Worker 子系统入口.
     *
     * @param workerClient       Worker 协议客户端
     * @param identityStore      Worker 本地身份存储
     * @param pluginRouter       插件路由器
     * @param executionStore     任务执行存储
     * @param coordinator        状态协调器
     * @param listenerRegistry   任务监听注册器
     * @param taskPool           任务动作线程池
     * @param heartbeatScheduler 心跳调度器
     * @param properties         Agent 配置
     * @return Worker 子系统入口
     */
    @Bean
    public WorkerService workerService(WorkerClient workerClient, AgentWorkerConfigStore identityStore,
            WorkerPluginRouter pluginRouter, WorkerTaskExecutionStore executionStore,
            WorkerTaskStateCoordinator coordinator, TaskStateListenerRegistry listenerRegistry,
            @Qualifier("agentTaskPool") ThreadPoolExecutor taskPool,
            @Qualifier("agentHeartbeatScheduler") ScheduledExecutorService heartbeatScheduler,
            AgentProperties properties) {
        return new WorkerService(workerClient, identityStore, pluginRouter, executionStore, coordinator,
                listenerRegistry, taskPool, heartbeatScheduler, SubmitModeEnum.SYNC,
                properties.getManager().getHeartbeatIntervalMs(),
                properties.getWorker().isAcceptTasksBeforeRegistered());
    }

    private RestTemplate managerRestTemplate(AgentProperties properties) {
        AgentProperties.Manager manager = properties.getManager();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.min(Math.max(manager.getConnectTimeoutMs(), 1L),
                Integer.MAX_VALUE));
        requestFactory.setReadTimeout((int) Math.min(Math.max(manager.getReadTimeoutMs(), 1L),
                Integer.MAX_VALUE));
        return new RestTemplate(requestFactory);
    }
}
