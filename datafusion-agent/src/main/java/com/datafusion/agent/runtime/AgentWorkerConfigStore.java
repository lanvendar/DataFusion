package com.datafusion.agent.runtime;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.model.Worker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Agent worker 本地配置存储.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/23
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentWorkerConfigStore {

    /**
     * 默认 worker 配置文件.
     */
    private static final Path WORKER_CONFIG_PATH = Paths.get(
            "/opt/datafusion-builtin/datafusion-agent/worker.config");

    /**
     * 读取本地 worker 配置.
     *
     * @return worker 配置
     */
    public Worker load() {
        File file = WORKER_CONFIG_PATH.toFile();
        if (!file.exists()) {
            return null;
        }
        try {
            return JacksonUtils.file2Bean(file, Worker.class);
        } catch (Exception e) {
            log.warn("读取 worker 本地配置失败, path={}", WORKER_CONFIG_PATH, e);
            return null;
        }
    }

    /**
     * 保存本地 worker 配置.
     *
     * @param worker worker 配置
     */
    public void save(Worker worker) {
        if (worker == null || worker.getId() == null) {
            return;
        }
        try {
            Files.createDirectories(WORKER_CONFIG_PATH.getParent());
            JacksonUtils.obj2File(WORKER_CONFIG_PATH.toFile(), worker);
        } catch (Exception e) {
            log.warn("保存 worker 本地配置失败, path={}", WORKER_CONFIG_PATH, e);
        }
    }
}
