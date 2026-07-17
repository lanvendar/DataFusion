package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.client.WorkerIdentityStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Agent worker 本地配置存储.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/23
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentWorkerConfigStore implements WorkerIdentityStore {

    /**
     * worker 配置文件.
     */
    private final Path workerConfigPath;

    /**
     * 构造函数.
     *
     * @param properties agent 配置
     */
    public AgentWorkerConfigStore(AgentProperties properties) {
        this.workerConfigPath = Path.of(properties.getWorker().getWorkerConfigPath());
    }

    /**
     * 读取本地 worker 配置.
     *
     * @return worker 配置
     */
    @Override
    public Optional<Worker> load() {
        File file = workerConfigPath.toFile();
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JacksonUtils.file2Bean(file, Worker.class));
        } catch (Exception e) {
            log.warn("读取 worker 本地配置失败, path={}", workerConfigPath, e);
            return Optional.empty();
        }
    }

    /**
     * 保存本地 worker 配置.
     *
     * @param worker worker 配置
     */
    @Override
    public void save(Worker worker) {
        if (worker == null || worker.getId() == null) {
            return;
        }
        try {
            Files.createDirectories(workerConfigPath.getParent());
            JacksonUtils.obj2File(workerConfigPath.toFile(), worker);
        } catch (Exception e) {
            log.warn("保存 worker 本地配置失败, path={}", workerConfigPath, e);
        }
    }

    /**
     * 删除本地 Worker 身份.
     */
    @Override
    public void delete() {
        try {
            Files.deleteIfExists(workerConfigPath);
        } catch (Exception e) {
            log.warn("删除 worker 本地配置失败, path={}", workerConfigPath, e);
        }
    }
}
