package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.rpc.ManagerClient;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateReportScheduler;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
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
     * 心跳调度器.
     */
    private final ScheduledExecutorService heartbeatScheduler;

    /**
     * 任务状态上报计划.
     */
    private final AgentTaskStateReportScheduler taskStateReportScheduler;

    /**
     * worker 本地配置存储.
     */
    private final AgentWorkerConfigStore workerConfigStore;

    /**
     * 构造函数.
     *
     * @param properties    agent 配置
     * @param managerClient manager client
     * @param runtimeState  agent 运行状态
     * @param router        插件路由
     * @param heartbeatScheduler 心跳调度器
     * @param taskStateReportScheduler 任务状态上报计划
     * @param workerConfigStore worker 本地配置存储
     */
    public AgentLifecycle(AgentProperties properties, ManagerClient managerClient, AgentRuntimeState runtimeState,
            WorkerTaskOperatorRouter router, @Qualifier("agentHeartbeatScheduler") ScheduledExecutorService heartbeatScheduler,
            AgentTaskStateReportScheduler taskStateReportScheduler, AgentWorkerConfigStore workerConfigStore) {
        this.properties = properties;
        this.managerClient = managerClient;
        this.runtimeState = runtimeState;
        this.router = router;
        this.heartbeatScheduler = heartbeatScheduler;
        this.taskStateReportScheduler = taskStateReportScheduler;
        this.workerConfigStore = workerConfigStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        initWorker();
        taskStateReportScheduler.start();
        long interval = Math.max(properties.getManager().getHeartbeatIntervalMs(), 1000L);
        heartbeatScheduler.scheduleWithFixedDelay(this::registerOrHeartbeat, 0L, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        Worker worker = runtimeState.getWorker();
        if (worker != null && runtimeState.isReady()) {
            managerClient.offline(offlineWorker(worker));
        }
        runtimeState.setReady(false);
    }

    private void initWorker() {
        Worker worker = new Worker();
        long now = System.currentTimeMillis();
        AgentProperties.Worker workerProperties = properties.getWorker();
        String resolvedIp = resolveIp(workerProperties);
        String resolvedHostName = resolveHostName(workerProperties);
        Integer port = workerProperties.getPort();
        worker.setWorkerCode(resolveWorkerCode(workerProperties, resolvedHostName, resolvedIp, port));
        mergeLocalWorker(worker);
        worker.setIp(firstNonBlank(resolvedIp, workerProperties.getDefaultIp()));
        worker.setPort(workerProperties.getPort());
        worker.setHostName(firstNonBlank(resolvedHostName, workerProperties.getDefaultHostName()));
        worker.setPluginTypes(new ArrayList<>(router.executors().keySet()));
        worker.setStatus(Worker.STATUS_UP);
        worker.setRegisterTime(now);
        worker.setLastHeartbeatTime(now);
        worker.setWorkerLogDir(resolveWorkerLogDir());
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
        worker.setWorkerLogDir(resolveWorkerLogDir());
        worker.setUpdateTime(now);
        Worker savedWorker = runtimeState.isReady() ? managerClient.heartbeat(heartbeatWorker(worker)) : managerClient.register(worker);
        boolean success = savedWorker != null;
        if (success) {
            mergeSavedWorker(worker, savedWorker);
            workerConfigStore.save(worker);
        }
        runtimeState.setReady(success);
        if (!success) {
            log.warn("agent 注册或心跳失败, workerId={}", worker.getId());
        }
    }

    private String resolveWorkerCode(AgentProperties.Worker workerProperties, String hostName, String ip, Integer port) {
        if (workerProperties.getWorkerCode() != null && !workerProperties.getWorkerCode().trim().isEmpty()) {
            return workerProperties.getWorkerCode().trim();
        }
        if (isNotBlank(hostName) && isNotBlank(ip) && port != null) {
            String source = hostName + SystemConstant.COLON + ip + SystemConstant.COLON + port;
            return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
        }
        return workerProperties.getDefaultWorkerCode();
    }

    private String resolveIp(AgentProperties.Worker workerProperties) {
        if (workerProperties.getIp() != null && !workerProperties.getIp().trim().isEmpty()) {
            return workerProperties.getIp();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveHostName(AgentProperties.Worker workerProperties) {
        if (workerProperties.getHostName() != null && !workerProperties.getHostName().trim().isEmpty()) {
            return workerProperties.getHostName();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveWorkerLogDir() {
        return properties.getWorker().getWorkerLogDir();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String first, String second) {
        return isNotBlank(first) ? first : second;
    }

    private void mergeSavedWorker(Worker worker, Worker savedWorker) {
        if (savedWorker.getId() != null) {
            worker.setId(savedWorker.getId());
        }
        if (savedWorker.getWorkerCode() != null) {
            worker.setWorkerCode(savedWorker.getWorkerCode());
        }
        if (savedWorker.getRegisterTime() != null) {
            worker.setRegisterTime(savedWorker.getRegisterTime());
        }
        if (savedWorker.getLastHeartbeatTime() != null) {
            worker.setLastHeartbeatTime(savedWorker.getLastHeartbeatTime());
        }
        if (savedWorker.getWorkerLogDir() != null) {
            worker.setWorkerLogDir(savedWorker.getWorkerLogDir());
        }
        if (savedWorker.getUpdateTime() != null) {
            worker.setUpdateTime(savedWorker.getUpdateTime());
        }
    }

    private void mergeLocalWorker(Worker worker) {
        Worker localWorker = workerConfigStore.load();
        if (localWorker == null || !worker.getWorkerCode().equals(localWorker.getWorkerCode())) {
            return;
        }
        worker.setId(localWorker.getId());
        if (localWorker.getRegisterTime() != null) {
            worker.setRegisterTime(localWorker.getRegisterTime());
        }
    }

    private Worker heartbeatWorker(Worker worker) {
        Worker heartbeat = new Worker();
        heartbeat.setId(worker.getId());
        heartbeat.setLastHeartbeatTime(worker.getLastHeartbeatTime());
        return heartbeat;
    }

    private Worker offlineWorker(Worker worker) {
        Worker offline = new Worker();
        offline.setId(worker.getId());
        return offline;
    }
}
