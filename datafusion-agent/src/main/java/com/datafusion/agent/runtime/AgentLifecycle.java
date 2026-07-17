package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agent Spring 生命周期适配器.
 *
 * <p>本类只把 Agent 配置转换为初始 Worker，并将启动、恢复、心跳和停止委托给 {@link WorkerService}。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentLifecycle implements ApplicationRunner, DisposableBean {

    /** Agent 配置. */
    private final AgentProperties properties;

    /** Worker 子系统入口. */
    private final WorkerService workerService;

    /**
     * 创建 Agent 生命周期适配器.
     *
     * @param properties    Agent 配置
     * @param workerService Worker 子系统入口
     */
    public AgentLifecycle(AgentProperties properties, WorkerService workerService) {
        this.properties = properties;
        this.workerService = workerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        workerService.start(createWorker());
    }

    @Override
    public void destroy() {
        workerService.stop();
    }

    private Worker createWorker() {
        AgentProperties.Worker config = properties.getWorker();
        String ip = firstText(config.getIp(), localAddress(), config.getDefaultIp());
        String hostName = firstText(config.getHostName(), localHostName(), config.getDefaultHostName());
        String workerCode = config.getWorkerCode();
        if (isBlank(workerCode) && !isBlank(hostName) && !isBlank(ip) && config.getPort() != null) {
            String identity = hostName + SystemConstant.COLON + ip + SystemConstant.COLON + config.getPort();
            workerCode = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
        }

        long now = System.currentTimeMillis();
        Worker worker = new Worker();
        worker.setWorkerCode(firstText(workerCode, config.getDefaultWorkerCode()));
        worker.setIp(ip);
        worker.setPort(config.getPort());
        worker.setHostName(hostName);
        worker.setPluginTypes(resolvePluginTypes(config.getPluginTypes()));
        worker.setStatus(Worker.STATUS_UP);
        worker.setRegisterTime(now);
        worker.setLastHeartbeatTime(now);
        worker.setWorkerLogDir(config.getWorkerLogDir());
        worker.setUpdateTime(now);
        return worker;
    }

    private List<String> resolvePluginTypes(String configuredTypes) {
        Set<String> loadedTypes = workerService.pluginTypes();
        if (isBlank(configuredTypes)) {
            return new ArrayList<>(loadedTypes);
        }
        List<String> resolved = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String item : configuredTypes.split(SystemConstant.COMMA)) {
            String pluginType = item == null ? null : item.trim();
            if (isBlank(pluginType)) {
                continue;
            }
            String loadedType = loadedTypes.stream()
                    .filter(type -> type.equalsIgnoreCase(pluginType)).findFirst().orElse(null);
            if (loadedType != null && !resolved.contains(loadedType)) {
                resolved.add(loadedType);
            } else if (loadedType == null) {
                unknown.add(pluginType);
            }
        }
        if (!unknown.isEmpty()) {
            log.warn("worker插件类型配置包含未加载插件, configured={}, loaded={}", unknown, loadedTypes);
        }
        return resolved;
    }

    private String localAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
