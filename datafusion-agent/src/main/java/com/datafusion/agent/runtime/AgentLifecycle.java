package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.rpc.ManagerClient;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateReportScheduler;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 生命周期管理.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentLifecycle implements ApplicationRunner, DisposableBean {

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * manager client.
     */
    private final ManagerClient managerClient;

    /**
     * agent 运行状态.
     */
    private final AgentRuntimeState runtimeState;

    /**
     * 插件路由.
     */
    private final WorkerTaskOperatorRouter router;

    /**
     * 心跳线程池.
     */
    private final ThreadPoolExecutor heartbeatPool;

    /**
     * 心跳调度器.
     */
    private final ScheduledExecutorService heartbeatScheduler;

    /**
     * 任务状态上报计划.
     */
    private final AgentTaskStateReportScheduler taskStateReportScheduler;

    /**
     * 构造函数.
     *
     * @param properties    agent 配置
     * @param managerClient manager client
     * @param runtimeState  agent 运行状态
     * @param router        插件路由
     * @param heartbeatPool 心跳线程池
     * @param heartbeatScheduler 心跳调度器
     * @param taskStateReportScheduler 任务状态上报计划
     */
    public AgentLifecycle(AgentProperties properties, ManagerClient managerClient, AgentRuntimeState runtimeState,
            WorkerTaskOperatorRouter router, @Qualifier("agentHeartbeatPool") ThreadPoolExecutor heartbeatPool,
            @Qualifier("agentHeartbeatScheduler") ScheduledExecutorService heartbeatScheduler,
            AgentTaskStateReportScheduler taskStateReportScheduler) {
        this.properties = properties;
        this.managerClient = managerClient;
        this.runtimeState = runtimeState;
        this.router = router;
        this.heartbeatPool = heartbeatPool;
        this.heartbeatScheduler = heartbeatScheduler;
        this.taskStateReportScheduler = taskStateReportScheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        initWorker();
        taskStateReportScheduler.start();
        long interval = Math.max(properties.getManager().getHeartbeatIntervalMs(), 1000L);
        heartbeatScheduler.scheduleWithFixedDelay(() -> heartbeatPool.execute(this::registerOrHeartbeat),
                0L, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        Worker worker = runtimeState.getWorker();
        if (worker != null && runtimeState.isReady()) {
            managerClient.offline(worker);
        }
        runtimeState.setReady(false);
    }

    private void initWorker() {
        Worker worker = new Worker();
        long now = System.currentTimeMillis();
        AgentProperties.Worker workerProperties = properties.getWorker();
        worker.setId(resolveWorkerId(workerProperties));
        worker.setIp(resolveIp(workerProperties));
        worker.setPort(workerProperties.getPort());
        worker.setHostName(resolveHostName(workerProperties));
        worker.setPluginTypes(new ArrayList<>(router.executors().keySet()));
        worker.setStatus(Worker.STATUS_UP);
        worker.setRegisterTime(now);
        worker.setLastHeartbeatTime(now);
        worker.setUpdateTime(now);
        runtimeState.setWorker(worker);
        runtimeState.setPluginTypes(worker.getPluginTypes());
    }

    private void registerOrHeartbeat() {
        Worker worker = runtimeState.getWorker();
        if (worker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        worker.setLastHeartbeatTime(now);
        worker.setUpdateTime(now);
        boolean success = runtimeState.isReady() ? managerClient.heartbeat(worker) : managerClient.register(worker);
        runtimeState.setReady(success);
        if (!success) {
            log.warn("agent 注册或心跳失败, workerId={}", worker.getId());
        }
    }

    private String resolveWorkerId(AgentProperties.Worker workerProperties) {
        if (workerProperties.getId() != null && !workerProperties.getId().trim().isEmpty()) {
            return workerProperties.getId();
        }
        String hostName = resolveHostName(workerProperties);
        Integer port = workerProperties.getPort();
        if (hostName != null && port != null) {
            return hostName + ':' + port;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveIp(AgentProperties.Worker workerProperties) {
        if (workerProperties.getIp() != null && !workerProperties.getIp().trim().isEmpty()) {
            return workerProperties.getIp();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private String resolveHostName(AgentProperties.Worker workerProperties) {
        if (workerProperties.getHostName() != null && !workerProperties.getHostName().trim().isEmpty()) {
            return workerProperties.getHostName();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
