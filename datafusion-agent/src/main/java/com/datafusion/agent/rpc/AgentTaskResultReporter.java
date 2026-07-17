package com.datafusion.agent.rpc;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.reporter.TaskResultReporter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Agent 任务结果 HTTP 上报器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
public final class AgentTaskResultReporter implements TaskResultReporter {

    /** 任务结果上报接口. */
    private static final String REPORT_PATH = "/internal/schedule/reportTaskResult";

    /** Manager 统一响应类型. */
    private static final TypeReference<Result<JsonNode>> RESULT_TYPE = new TypeReference<>() {
    };

    /** HTTP 客户端. */
    private final RestTemplate restTemplate;

    /** Agent 配置. */
    private final AgentProperties properties;

    /**
     * 创建任务结果上报器.
     *
     * @param restTemplate HTTP 客户端
     * @param properties   Agent 配置
     */
    public AgentTaskResultReporter(RestTemplate restTemplate, AgentProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public boolean report(TaskResult result) {
        if (result == null) {
            return false;
        }
        if (!properties.getManager().isEnabled()) {
            return true;
        }
        String baseUrl = properties.getManager().getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            log.warn("manager baseUrl 未配置, taskInstanceId={}", result.getTaskInstanceId());
            return false;
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + REPORT_PATH, result, String.class);
            Result<JsonNode> managerResult = response.getStatusCode().is2xxSuccessful()
                    ? JacksonUtils.tryStr2Bean(response.getBody(), RESULT_TYPE) : null;
            boolean reported = managerResult != null
                    && ErrorCodeEnum.SUCCESS.getCode().equals(managerResult.getCode())
                    && managerResult.getData() != null && managerResult.getData().asBoolean(false);
            if (!reported) {
                log.warn("任务结果上报失败, taskInstanceId={}", result.getTaskInstanceId());
            }
            return reported;
        } catch (Exception e) {
            log.warn("任务结果上报异常, taskInstanceId={}", result.getTaskInstanceId(), e);
            return false;
        }
    }
}
