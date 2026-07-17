package com.datafusion.agent.rpc;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.client.WorkerClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Agent 到 Manager 的 Worker 协议客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
public final class AgentWorkerClient implements WorkerClient {

    /** Manager 统一响应类型. */
    private static final TypeReference<Result<JsonNode>> RESULT_TYPE = new TypeReference<>() {
    };

    /** Worker 注册接口. */
    private static final String REGISTER_PATH = "/internal/schedule/worker/register";

    /** Worker 心跳接口. */
    private static final String HEARTBEAT_PATH = "/internal/schedule/worker/heartbeat";

    /** Worker 下线接口. */
    private static final String OFFLINE_PATH = "/internal/schedule/worker/offline";

    /** Worker 未完成任务接口. */
    private static final String WORKER_TASKS_PATH = "/internal/schedule/worker/tasks";

    /** HTTP 客户端. */
    private final RestTemplate restTemplate;

    /** Agent 配置. */
    private final AgentProperties properties;

    /**
     * 创建 Worker 协议客户端.
     *
     * @param restTemplate HTTP 客户端
     * @param properties   Agent 配置
     */
    public AgentWorkerClient(RestTemplate restTemplate, AgentProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public Worker register(Worker worker) {
        if (!properties.getManager().isEnabled()) {
            if (worker.getId() == null) {
                worker.setId(worker.getWorkerCode());
            }
            return worker;
        }
        return postWorker(REGISTER_PATH, worker);
    }

    @Override
    public Worker heartbeat(Worker worker) {
        return properties.getManager().isEnabled() ? postWorker(HEARTBEAT_PATH, worker) : worker;
    }

    @Override
    public Worker offline(Worker worker) {
        return properties.getManager().isEnabled() ? postWorker(OFFLINE_PATH, worker) : worker;
    }

    @Override
    public Optional<List<TaskRequest>> findUnfinishedTasks(Worker worker) {
        if (!properties.getManager().isEnabled()) {
            return Optional.of(Collections.emptyList());
        }
        Result<JsonNode> result = post(WORKER_TASKS_PATH, worker);
        if (!success(result) || result.getData() == null) {
            return Optional.empty();
        }
        List<TaskRequest> tasks = JacksonUtils.tryObj2Bean(result.getData(),
                new TypeReference<List<TaskRequest>>() {
                });
        return Optional.of(tasks == null ? Collections.emptyList() : tasks);
    }

    private Worker postWorker(String path, Worker worker) {
        Result<JsonNode> result = post(path, worker);
        return !success(result) || result.getData() == null
                ? null : JacksonUtils.tryObj2Bean(result.getData(), Worker.class);
    }

    private Result<JsonNode> post(String path, Object body) {
        String baseUrl = properties.getManager().getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            log.warn("manager baseUrl 未配置, path={}", path);
            return null;
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + path, body, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            return JacksonUtils.tryStr2Bean(response.getBody(), RESULT_TYPE);
        } catch (Exception e) {
            log.warn("调用 manager Worker 协议失败, path={}", path, e);
            return null;
        }
    }

    private boolean success(Result<?> result) {
        return result != null && ErrorCodeEnum.SUCCESS.getCode().equals(result.getCode());
    }
}
