package com.datafusion.agent.config;

import com.datafusion.agent.client.HttpManagerClient;
import com.datafusion.agent.client.ManagerClient;
import com.datafusion.agent.runtime.AgentExecutionStatusRecorder;
import com.datafusion.agent.runtime.AgentRuntimeState;
import com.datafusion.agent.runtime.task.AgentWorkerTaskContextStorage;
import com.datafusion.agent.runtime.FileAgentExecutionStatusRecorder;
import com.datafusion.agent.runtime.task.ManagerTaskResultReporter;
import com.datafusion.common.threadpool.NamedThreadFactory;
import com.datafusion.common.threadpool.ThreadPoolBuilder;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.worker.WorkerTaskOperator;
import com.datafusion.scheduler.worker.WorkerTaskService;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerPluginLoader;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
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
     * @param statusRecorder 执行状态记录器
     * @return worker 任务上下文存储
     */
    @Bean
    public WorkerTaskContextStorage workerTaskContextStore(AgentExecutionStatusRecorder statusRecorder) {
        return new AgentWorkerTaskContextStorage(statusRecorder);
    }

    /**
     * agent 执行状态记录器.
     *
     * @param properties agent 配置
     * @param state      agent 运行状态
     * @return agent 执行状态记录器
     */
    @Bean
    public AgentExecutionStatusRecorder agentExecutionStatusRecorder(AgentProperties properties, AgentRuntimeState state) {
        return new FileAgentExecutionStatusRecorder(properties, state);
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
