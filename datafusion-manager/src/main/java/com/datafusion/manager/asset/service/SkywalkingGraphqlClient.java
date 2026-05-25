package com.datafusion.manager.asset.service;

import com.datafusion.manager.asset.dto.skywalking.BasicTraceDto;
import com.datafusion.manager.asset.dto.skywalking.EndpointDto;
import com.datafusion.manager.asset.dto.skywalking.EndpointGraphqlResponse;
import com.datafusion.manager.asset.dto.skywalking.GraphqlServiceResponse;
import com.datafusion.manager.asset.dto.skywalking.QueryBasicTracesGraphqlResponse;
import com.datafusion.manager.asset.dto.skywalking.QueryTraceGraphqlResponse;
import com.datafusion.manager.asset.dto.skywalking.SkyWalkingServiceDto;
import com.datafusion.manager.asset.dto.skywalking.SpanDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SkywalkingGraphQLClient.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Slf4j
@Service
public class SkywalkingGraphqlClient {

    /**
     * restTemplate.
     */
    private final RestTemplate restTemplate;

    /**
     * graphqlEndpoint.
     */
    private final String graphqlEndpoint;

    /**
     * objectMapper.
     */
    private final ObjectMapper objectMapper;

    /**
     * 重试时间间隔（单位：毫秒）.
     */
    private static final long RETRY_SLEEP_MS = 5000; // 5秒

    /**
     * 最大重试次数.
     */
    private static final int MAX_RETRIES = 10;

    /**
     * SkywalkingGraphqlClient构造函数.
     *
     * @param restTemplate    restTemplate
     * @param graphqlEndpoint graphqlEndpoint
     * @param objectMapper    objectMapper
     */
    public SkywalkingGraphqlClient(@org.springframework.beans.factory.annotation.Qualifier("skywalkingRestTemplate") RestTemplate restTemplate,
            @Value("${skywalking.graphql.url}") String graphqlEndpoint,
            ObjectMapper objectMapper) {
        // 手动初始化一个绕过 SSL 的 RestTemplate
        this.restTemplate = createInsecureRestTemplate();
        this.graphqlEndpoint = graphqlEndpoint;
        this.objectMapper = objectMapper;
    }

    /**
     * createInsecureRestTemplate.
     * @return RestTemplate
     */
    private RestTemplate createInsecureRestTemplate() {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);
            return new RestTemplate(requestFactory);
        } catch (Exception e) {
            log.error("创建不安全 RestTemplate 失败，将使用默认配置", e);
            return new RestTemplate();
        }
    }

    /**
     * GET_ALL_SERVICES_QUERY.
     */
    private static final String GET_ALL_SERVICES_QUERY = "query GetAllServicesQuery($start: String!, $end: String!, $step: Step!) {\n"
            + "    getAllServices(duration: {\n"
            + "        start: $start,\n"
            + "        end: $end,\n"
            + "        step: $step\n"
            + "    }) {\n"
            + "        id\n"
            + "        name\n"
            + "        shortName\n"
            + "        __typename\n"
            + "    }\n"
            + "}";

    /**
     * FIND_ENDPOINTS_QUERY.
     */
    private static final String FIND_ENDPOINTS_QUERY = "query FindEndpointQuery($serviceId: ID!, $keyword: String, $limit: Int!) {\n"
            + "    findEndpoint(\n"
            + "        serviceId: $serviceId,\n"
            + "        keyword: $keyword,\n"
            + "        limit: $limit\n"
            + "    ) {\n"
            + "        id\n"
            + "        name\n"
            + "    }\n"
            + "}";

    /**
     * QUERY_BASIC_TRACES_QUERY.
     */
    private static final String QUERY_BASIC_TRACES_QUERY = "query QueryBasicTracesQuery($condition: TraceQueryCondition!) {\n"
            + "    queryBasicTraces(condition: $condition) {\n"
            + "        traces {\n"
            + "            traceIds\n"
            + "            segmentId\n"
            + "            endpointNames\n"
            + "            duration\n"
            + "            start\n"
            + "            isError\n"
            + "        }\n"
            + "    }\n"
            + "}";

    /**
     * QUERY_TRACE_QUERY.
     */
    private static final String QUERY_TRACE_QUERY = "query TraceQuery($traceId: ID!) {\n"
            + "  queryTrace(traceId: $traceId) {\n"
            + "    spans {\n"
            + "      traceId\n"
            + "      segmentId\n"
            + "      spanId\n"
            + "      parentSpanId\n"
            + "      refs {\n"
            + "        parentSegmentId\n"
            + "        parentSpanId\n"
            + "      }"
            + "      serviceCode\n"
            + "      endpointName\n"
            + "      startTime\n"
            + "      endTime\n"
            + "      component\n"
            + "      type\n"
            + "      peer\n"
            + "      isError\n"
            + "      layer\n"
            + "      tags {\n"
            + "        key\n"
            + "        value\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

    /**
     * 查询服务的topo依赖.
     */
    private static final String QUERY_SERVICE_TOPOLOGY = "query {\n"
            + "  getServiceTopology(\n"
            + "    serviceId: $serviceId,\n"
            + "    duration: {\n"
            + "    start: $starTime,\n"
            + "    end: $endTime,\n"
            + "    step: HOUR\n"
            + "  }){\n"
            + "    nodes {\n"
            + "      id\n"
            + "      name\n"
            + "      type\n"
            + "      isReal\n"
            + "    }\n"
            + "    calls {\n"
            + "      id\n"
            + "      source\n"
            + "      target\n"
            + "    }\n"
            + "  }\n"
            + "}";

    /**
     * 查询 Skywalking 中指定时间范围内的所有服务.
     *
     * @param startTime 查询开始时间 (格式: YYYY-MM-DD HHMM, e.g., "2025-10-11 0000")
     * @param endTime   查询结束时间 (格式: YYYY-MM-DD HHMM, e.g., "2025-10-11 2300")
     * @param step      时间步长 (GraphQL Step 枚举值，例如: "DAY")
     * @return 服务列表 (ServiceDto)
     */
    public List<SkyWalkingServiceDto> getAllServices(String startTime, String endTime, String step) {

        // 1. 构造 GraphQL 请求体

        // 使用变量传递参数 (最佳实践，避免字符串拼接)
        Map<String, Object> variables = new HashMap<>();
        variables.put("start", startTime);
        variables.put("end", endTime);
        variables.put("step", step); // 假设 GraphQL 接口接受字符串形式的 Step

        // 构建最终的请求 Map
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", GET_ALL_SERVICES_QUERY);
        requestBody.put("variables", variables);

        // 2. 构造 HTTP Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // 3. 执行请求并获取响应 (重试循环)
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                GraphqlServiceResponse response = restTemplate.postForObject(
                        graphqlEndpoint,
                        requestEntity,
                        GraphqlServiceResponse.class);

                // 4. 解析结果 (成功返回)
                if (response != null && response.getData() != null) {
                    return response.getData().getGetAllServices();
                }
                return Collections.emptyList();

            } catch (HttpServerErrorException e) {
                if (handleRetryException(e, attempt, "getAllServices", "503 Service Unavailable")) {
                    continue; // 睡眠并重试
                } else {
                    // 达到最大重试次数或非503错误，抛出
                    throw new RuntimeException("Server error during getAllServices. Status: " + e.getStatusCode(), e);
                }
            } catch (ResourceAccessException | HttpClientErrorException e) {
                if (handleRetryException(e, attempt, "getAllServices", "Connection/Client Error")) {
                    continue;
                } else {
                    throw new RuntimeException("Client or connection error during getAllServices.", e);
                }
            } catch (Exception e) {
                // 捕获其他未知异常，直接抛出
                throw new RuntimeException("Failed to query Skywalking GraphQL endpoint for services.", e);
            }
        }
        return Collections.emptyList(); // 理论上不应该执行到这里
    }

    /**
     * 查询 Skywalking 中指定时间范围内的所有服务.
     *
     * @param startTime 查询开始时间 (格式: YYYY-MM-DD HHMM, e.g., "2025-10-11 0000")
     * @param endTime   查询结束时间 (格式: YYYY-MM-DD HHMM, e.g., "2025-10-11 2300")
     * @param serviceId skywalking 服务id
     * @return 服务列表 (ServiceDto)
     */
    public List<SkyWalkingServiceDto> getServiceTopology(String startTime, String endTime, String serviceId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("start", startTime);
        variables.put("end", endTime);
        variables.put("serviceId", serviceId);

        // 构建最终的请求 Map
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", QUERY_SERVICE_TOPOLOGY);
        requestBody.put("variables", variables);

        // 2. 构造 HTTP Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // 3. 执行请求并获取响应 (重试循环)
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                GraphqlServiceResponse response = restTemplate.postForObject(
                        graphqlEndpoint,
                        requestEntity,
                        GraphqlServiceResponse.class);

                // 4. 解析结果 (成功返回)
                if (response != null && response.getData() != null) {
                    return response.getData().getGetAllServices();
                }
                return Collections.emptyList();

            } catch (HttpServerErrorException e) {
                if (handleRetryException(e, attempt, "getAllServices", "503 Service Unavailable")) {
                    continue; // 睡眠并重试
                } else {
                    // 达到最大重试次数或非503错误，抛出
                    throw new RuntimeException("Server error during getAllServices. Status: " + e.getStatusCode(), e);
                }
            } catch (ResourceAccessException | HttpClientErrorException e) {
                if (handleRetryException(e, attempt, "getAllServices", "Connection/Client Error")) {
                    continue;
                } else {
                    throw new RuntimeException("Client or connection error during getAllServices.", e);
                }
            } catch (Exception e) {
                // 捕获其他未知异常，直接抛出
                throw new RuntimeException("Failed to query Skywalking GraphQL endpoint for services.", e);
            }
        }
        return Collections.emptyList(); // 理论上不应该执行到这里
    }

    /**
     * 查询指定服务下的 Endpoint 列表.
     *
     * @param serviceId 服务的唯一标识 ID
     * @param keyword   接口名称的过滤关键字 (例如 "POST:/api")
     * @param limit     返回的最大数量
     * @return Endpoint 列表
     */
    public List<EndpointDto> findEndpoints(String serviceId, String keyword, int limit) {

        // 1. 构造 GraphQL 请求体
        Map<String, Object> variables = new HashMap<>();
        variables.put("serviceId", serviceId);
        if (StringUtils.isNotEmpty(keyword)) {
            variables.put("keyword", keyword);
        }
        variables.put("limit", limit);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", FIND_ENDPOINTS_QUERY);
        requestBody.put("variables", variables);

        // 2. 构造 HTTP Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // 3. 执行请求并获取响应 (重试循环)
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                EndpointGraphqlResponse response = restTemplate.postForObject(
                        graphqlEndpoint,
                        requestEntity,
                        EndpointGraphqlResponse.class);

                // 4. 解析结果 (成功返回)
                if (response != null && response.getData() != null) {
                    return response.getData().getFindEndpoint();
                }
                return Collections.emptyList();

            } catch (HttpServerErrorException e) {
                if (handleRetryException(e, attempt, "findEndpoints", "503 Service Unavailable")) {
                    continue; // 睡眠并重试
                } else {
                    throw new RuntimeException("Server error during findEndpoints. Status: " + e.getStatusCode(), e);
                }
            } catch (ResourceAccessException | HttpClientErrorException e) {
                if (handleRetryException(e, attempt, "findEndpoints", "Connection/Client Error")) {
                    continue;
                } else {
                    throw new RuntimeException("Client or connection error during findEndpoints.", e);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to query Skywalking GraphQL endpoint for endpoints.", e);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 查询 Skywalking 中满足条件的链路基本信息.
     *
     * @param serviceId  服务ID
     * @param endpointId Endpoint ID (可选，可传 null)
     * @param startTime  查询开始时间 (格式: YYYY-MM-DD HHMM)
     * @param endTime    查询结束时间 (格式: YYYY-MM-DD HHMM)
     * @param step       时间步长 (例如: "MINUTE", "HOUR", "DAY")
     * @param traceState 链路状态 (例如: "ALL", "ERROR", "SUCCESS")
     * @param queryOrder 排序方式 (例如: "BY_START_TIME")
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 链路基本信息列表
     */
    public List<BasicTraceDto> queryBasicTraces(
            String serviceId, String endpointId,
            String startTime, String endTime, String step,
            String traceState, String queryOrder,
            int pageNum, int pageSize) {

        // 1. 构造嵌套的查询变量结构

        // 1.1 构造 queryDuration 变量
        Map<String, String> duration = new HashMap<>();
        duration.put("start", startTime);
        duration.put("end", endTime);
        duration.put("step", step);

        // 1.2 构造 paging 变量
        Map<String, Integer> paging = new HashMap<>();
        paging.put("pageNum", pageNum);
        paging.put("pageSize", pageSize);

        // 1.3 构造顶层 condition 变量
        Map<String, Object> condition = new HashMap<>();
        condition.put("serviceId", serviceId);
        // Endpoint ID 只有在非空时才加入，避免 GraphQL 报错
        if (endpointId != null && !endpointId.isEmpty()) {
            condition.put("endpointId", endpointId);
        }
        condition.put("queryDuration", duration);
        condition.put("traceState", traceState);
        condition.put("queryOrder", queryOrder);
        condition.put("paging", paging);

        // 2. 构造 GraphQL 请求体
        // 注意：由于我们在 GraphQL query 中使用了 $condition 变量，并且 $condition 自身是复杂对象
        // 我们在 requestBody.variables 中需要包装这一层。
        Map<String, Object> variables = new HashMap<>();
        variables.put("condition", condition);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", QUERY_BASIC_TRACES_QUERY);
        requestBody.put("variables", variables);

        // 3. 执行请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // 3. 执行请求并获取响应 (重试循环)
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                QueryBasicTracesGraphqlResponse response = restTemplate.postForObject(
                        graphqlEndpoint,
                        requestEntity,
                        QueryBasicTracesGraphqlResponse.class);

                // 4. 解析结果
                if (response != null && response.getData() != null && response.getData().getQueryBasicTraces() != null) {
                    return response.getData().getQueryBasicTraces().getTraces();
                }
                return Collections.emptyList();

            } catch (HttpServerErrorException e) {
                if (handleRetryException(e, attempt, "queryBasicTraces", "503 Service Unavailable")) {
                    continue;
                } else {
                    throw new RuntimeException("Server error during queryBasicTraces. Status: " + e.getStatusCode(), e);
                }
            } catch (ResourceAccessException | HttpClientErrorException e) {
                if (handleRetryException(e, attempt, "queryBasicTraces", "Connection/Client Error")) {
                    continue;
                } else {
                    throw new RuntimeException("Client or connection error during queryBasicTraces.", e);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to query basic traces from Skywalking.", e);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 查询单个 Trace ID 对应的完整链路 Span 详情.
     *
     * @param traceId 完整的链路 ID
     * @return 链路中包含的所有 Span 详情列表
     */
    public List<SpanDto> queryTrace(String traceId) {

        // 1. 构造 GraphQL 请求体
        Map<String, Object> variables = new HashMap<>();
        variables.put("traceId", traceId);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", QUERY_TRACE_QUERY);
        requestBody.put("variables", variables);

        // 2. 构造 HTTP Headers 和 Entity
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // 3. 执行请求
        QueryTraceGraphqlResponse response;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = restTemplate.postForObject(
                        graphqlEndpoint,
                        requestEntity,
                        QueryTraceGraphqlResponse.class);
                if (response != null && response.getData() != null && response.getData().getQueryTrace() != null) {
                    // 返回 Span 列表
                    return response.getData().getQueryTrace().getSpans();
                }
                return Collections.emptyList();
            } catch (HttpServerErrorException e) {
                // 捕获服务器端错误，例如 5xx 状态码
                if (e.getStatusCode().value() == 503) {
                    log.info("[Trace ID: %s] 尝试 %d/%d 失败: 收到 503 Service Unavailable. 准备重试...\n", traceId, attempt, MAX_RETRIES);
                    // 如果是最后一次尝试，则抛出异常
                    if (attempt == MAX_RETRIES) {
                        throw new RuntimeException("Failed to query trace details after " + MAX_RETRIES + " attempts (Last error: 503)", e);
                    }
                    // 睡眠并重试
                    sleep(RETRY_SLEEP_MS);
                    continue; // 进入下一次循环
                } else {
                    // 其他 5xx 错误（如 500 Internal Server Error），不重试，直接抛出
                    throw new RuntimeException("Server error during trace query for Trace ID: " + traceId + ". Status: " + e.getStatusCode(), e);
                }
            } catch (ResourceAccessException | HttpClientErrorException e) {
                // ResourceAccessException: 网络问题，连接超时等。
                // HttpClientErrorException: 4xx 客户端错误。
                log.error("[Trace ID: %s] 尝试 %d/%d 失败: 连接或客户端错误。准备重试...\n", traceId, attempt, MAX_RETRIES);
                // 对于网络不稳定引发的 ResourceAccessException，也可以进行重试
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Failed to query trace details after " + MAX_RETRIES + " attempts.", e);
                }
                sleep(RETRY_SLEEP_MS);
                continue;
            } catch (Exception e) {
                // 捕获其他未知异常，直接抛出
                throw new RuntimeException("Unexpected failure during trace query for Trace ID: " + traceId, e);
            }
        }
        // 理论上不会执行到这里，因为循环内一定会返回或抛出异常
        return Collections.emptyList();
    }

    /**
     * 辅助方法：处理睡眠时的中断.
     *
     * @param milliseconds 睡眠时间
     */
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("重试等待被中断。");
        }
    }

    /**
     * 辅助方法：处理异常并决定是否进行重试和睡眠.
     *
     * @param e          抛出的异常
     * @param attempt    当前重试次数
     * @param methodName 当前执行的方法名
     * @param errorType  错误类型描述
     * @return 如果应该重试，返回 true；如果达到最大次数或非重试错误，返回 false。
     */
    private boolean handleRetryException(Exception e, int attempt, String methodName, String errorType) {
        boolean is503 = (e instanceof HttpServerErrorException && ((HttpServerErrorException) e).getStatusCode().value() == 503);
        boolean isRetryable = is503 || e instanceof ResourceAccessException; // 503或网络连接问题都重试

        if (isRetryable && attempt < MAX_RETRIES) {
            log.error("[%s] 尝试 %d/%d 失败: %s. 准备等待 %dms 后重试...\n", methodName, attempt, MAX_RETRIES, errorType, RETRY_SLEEP_MS);
            sleep(RETRY_SLEEP_MS);
            return true;
        }

        return false;
    }

}
