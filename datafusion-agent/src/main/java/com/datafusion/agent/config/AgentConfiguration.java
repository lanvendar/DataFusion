package com.datafusion.agent.config;

import com.datafusion.agent.rpc.HttpManagerClient;
import com.datafusion.agent.rpc.ManagerClient;
import com.datafusion.agent.rpc.ManagerTaskResultReporter;
import com.datafusion.agent.runtime.AgentRuntimeState;
import com.datafusion.agent.runtime.worker.context.AgentWorkerTaskContextStorage;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateReportScheduler;
import com.datafusion.agent.runtime.worker.reporter.FileWorkerTaskExecutionStateStore;
import com.datafusion.common.threadpool.NamedThreadFactory;
import com.datafusion.common.threadpool.ThreadPoolBuilder;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.worker.WorkerTaskOperator;
import com.datafusion.scheduler.worker.WorkerTaskService;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerPluginLoader;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 运行时装配.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Configuration
public class AgentConfiguration {

    /**
     * 任务控制线程池.
     *
     * @param properties agent 配置
     * @return 任务控制线程池
     */
    @Bean(name = "agentTaskControlPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentTaskControlPool(AgentProperties properties) {
        return buildThreadPool("agent-task-control", properties.getTaskControlPool());
    }

    /**
     * 任务运行线程池.
     *
     * @param properties agent 配置
     * @return 任务运行线程池
     */
    @Bean(name = "agentTaskRunPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentTaskRunPool(AgentProperties properties) {
        return buildThreadPool("agent-task-run", properties.getTaskRunPool());
    }

    /**
     * 结果上报线程池.
     *
     * @param properties agent 配置
     * @return 结果上报线程池
     */
    @Bean(name = "agentResultReportPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentResultReportPool(AgentProperties properties) {
        return buildThreadPool("agent-result-report", properties.getResultReportPool());
    }

    /**
     * 心跳线程池.
     *
     * @param properties agent 配置
     * @return 心跳线程池
     */
    @Bean(name = "agentHeartbeatPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentHeartbeatPool(AgentProperties properties) {
        return buildThreadPool("agent-heartbeat", properties.getHeartbeatPool());
    }

    /**
     * 心跳调度器.
     *
     * @return 心跳调度器
     */
    @Bean(name = "agentHeartbeatScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService agentHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("agent-heartbeat-scheduler"));
    }

    /**
     * 状态刷新调度器.
     *
     * @return 状态刷新调度器
     */
    @Bean(name = "agentStateRefreshScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService agentStateRefreshScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("agent-state-refresh-scheduler"));
    }

    /**
     * 恢复线程池.
     *
     * @param properties agent 配置
     * @return 恢复线程池
     */
    @Bean(name = "agentRecoveryPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor agentRecoveryPool(AgentProperties properties) {
        return buildThreadPool("agent-recovery", properties.getRecoveryPool());
    }

    /**
     * RestTemplate.
     *
     * @return RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * manager client.
     *
     * @param restTemplate RestTemplate
     * @param properties   agent 配置
     * @return manager client
     */
    @Bean
    public ManagerClient managerClient(RestTemplate restTemplate, AgentProperties properties) {
        return new HttpManagerClient(restTemplate, properties);
    }

    /**
     * agent 运行状态.
     *
     * @return agent 运行状态
     */
    @Bean
    public AgentRuntimeState agentRuntimeState() {
        return new AgentRuntimeState();
    }

    /**
     * 插件加载器.
     *
     * @param executors 插件执行器列表
     * @return 插件加载器
     */
    @Bean
    public WorkerPluginLoader workerPluginLoader(List<PluginTaskExecutor> executors) {
        List<PluginTaskExecutor> plugins = executors == null ? Collections.emptyList() : executors;
        return () -> plugins;
    }

    /**
     * worker 插件路由.
     *
     * @param loader 插件加载器
     * @return worker 插件路由
     */
    @Bean
    public WorkerTaskOperatorRouter workerTaskOperatorRouter(WorkerPluginLoader loader) {
        return WorkerTaskOperatorRouter.fromLoader(loader);
    }

    /**
     * worker 任务上下文存储.
     *
     * @param stateStore 任务执行状态存储
     * @return worker 任务上下文存储
     */
    @Bean
    public WorkerTaskContextStorage workerTaskContextStore(WorkerTaskExecutionStateStore stateStore) {
        return new AgentWorkerTaskContextStorage(stateStore);
    }

    /**
     * worker 任务执行状态存储.
     *
     * @param properties agent 配置
     * @param state      agent 运行状态
     * @return worker 任务执行状态存储
     */
    @Bean
    public WorkerTaskExecutionStateStore workerTaskExecutionStateStore(AgentProperties properties,
            AgentRuntimeState state) {
        return new FileWorkerTaskExecutionStateStore(properties, state);
    }

    /**
     * 任务结果上报器.
     *
     * @param managerClient manager client
     * @param reportPool    上报线程池
     * @return 任务结果上报器
     */
    @Bean
    public TaskResultReporter taskResultReporter(ManagerClient managerClient,
            @Qualifier("agentResultReportPool") ThreadPoolExecutor reportPool) {
        return new ManagerTaskResultReporter(managerClient, reportPool);
    }

    /**
     * agent 任务状态上报计划.
     *
     * @param stateStore    任务执行状态存储
     * @param reporter      任务结果上报器
     * @param scheduler     状态刷新调度器
     * @param stateMappings 插件运行模式状态映射
     * @param properties    agent 配置
     * @return agent 任务状态上报计划
     */
    @Bean
    public AgentTaskStateReportScheduler agentTaskStateReportScheduler(WorkerTaskExecutionStateStore stateStore,
            TaskResultReporter reporter, @Qualifier("agentStateRefreshScheduler") ScheduledExecutorService scheduler,
            List<PluginRunModeStateMapping> stateMappings, AgentProperties properties) {
        AgentProperties.StateRefresh config = properties.getStateRefresh();
        return new AgentTaskStateReportScheduler(stateStore, reporter, scheduler, stateMappings,
                config.getIntervalMs(), config.getUnknownThreshold());
    }

    /**
     * worker 任务操作入口.
     *
     * @param router        插件路由
     * @param contextStore  上下文存储
     * @param reporter      结果上报器
     * @param taskRunPool   任务运行线程池
     * @return worker 任务操作入口
     */
    @Bean
    public WorkerTaskOperator workerTaskOperator(WorkerTaskOperatorRouter router, WorkerTaskContextStorage contextStore,
            TaskResultReporter reporter, @Qualifier("agentTaskRunPool") ThreadPoolExecutor taskRunPool) {
        return new WorkerTaskService(router, contextStore, reporter, taskRunPool, SubmitModeEnum.SYNC);
    }

    private ThreadPoolExecutor buildThreadPool(String poolName, AgentProperties.ThreadPoolConfig config) {
        return ThreadPoolBuilder.create()
                .setCorePoolSize(config.getCorePoolSize())
                .setMaxPoolSize(config.getMaxPoolSize())
                .setQueueCapacity(config.getQueueCapacity())
                .setKeepAliveSeconds(config.getKeepAliveSeconds())
                .setPoolName(poolName)
                .build();
    }
}
