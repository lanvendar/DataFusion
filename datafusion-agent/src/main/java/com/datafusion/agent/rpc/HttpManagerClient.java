package com.datafusion.agent.rpc;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP Manager 通信客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Slf4j
public class HttpManagerClient implements ManagerClient {

    /**
     * 注册接口.
     */
    private static final String REGISTER_PATH = "/internal/schedule/worker/register";

    /**
     * 心跳接口.
     */
    private static final String HEARTBEAT_PATH = "/internal/schedule/worker/heartbeat";

    /**
     * 下线接口.
     */
    private static final String OFFLINE_PATH = "/internal/schedule/worker/offline";

    /**
     * 任务结果上报接口.
     */
    private static final String REPORT_TASK_RESULT_PATH = "/internal/schedule/reportTaskResult";

    /**
     * Manager 统一响应类型.
     */
    private static final TypeReference<Result<JsonNode>> RESULT_TYPE = new TypeReference<>() {
    };

    /**
     * RestTemplate.
     */
    private final RestTemplate restTemplate;

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * 构造函数.
     *
     * @param restTemplate RestTemplate
     * @param properties   agent 配置
     */
    public HttpManagerClient(RestTemplate restTemplate, AgentProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public Worker register(Worker worker) {
        return post(REGISTER_PATH, worker);
    }

    @Override
    public Worker heartbeat(Worker worker) {
        return post(HEARTBEAT_PATH, worker);
    }

    @Override
    public Worker offline(Worker worker) {
        return post(OFFLINE_PATH, worker);
    }

    @Override
    public boolean reportTaskResult(TaskResult result) {
        return postBoolean(REPORT_TASK_RESULT_PATH, result);
    }

    private Worker post(String path, Worker body) {
        Result<JsonNode> result = postForResult(path, body);
        if (result == null || !ErrorCodeEnum.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return null;
        }
        return JacksonUtils.tryObj2Bean(result.getData(), Worker.class);
    }

    private boolean postBoolean(String path, Object body) {
        Result<JsonNode> result = postForResult(path, body);
        return result != null && ErrorCodeEnum.SUCCESS.getCode().equals(result.getCode());
    }

    private Result<JsonNode> postForResult(String path, Object body) {
        String baseUrl = properties.getManager().getBaseUrl();
        if (!properties.getManager().isEnabled()) {
            return Result.success(JacksonUtils.tryObj2JsonNode(body));
        }
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
            log.warn("调用 manager 失败, path={}", path, e);
            return null;
        }
    }
}
