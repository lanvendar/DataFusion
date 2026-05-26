package com.datafusion.manager.utils.asset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SkywalkingGraphqlClient 调试工具类
 *
 * 使用方法：
 * 1. 修改下面的 SKYWALKING_ENDPOINT 为你的 Skywalking GraphQL 地址
 * 2. 修改 TEST_TRACE_ID 为要查询的 traceId
 * 3. 直接运行 main 方法即可
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/22
 * @since 2025/10/22
 */
@Slf4j
public class SkywalkingGraphqlClientDebug {

    /**
     * Skywalking GraphQL endpoint 地址
     * 请根据实际环境修改
     */
    private static final String SKYWALKING_ENDPOINT = "http://localhost:12800/graphql";

    /**
     * 要查询的 Trace ID
     * 请根据实际需要修改
     */
    private static final String TEST_TRACE_ID = "example-trace-id-12345";

    private static final String QUERY_TRACE_QUERY = "query TraceQuery($traceId: ID!) {\n" +
            "          queryTrace(traceId: $traceId) {\n" +
            "            spans {\n" +
            "              traceId\n" +
            "              segmentId\n" +
            "              spanId\n" +
            "              parentSpanId\n" +
            "              serviceCode\n" +
            "              endpointName\n" +
            "              startTime\n" +
            "              endTime\n" +
            "              component\n" +
            "              type\n" +
            "              peer\n" +
            "              isError\n" +
            "              layer\n" +
            "              tags {\n" +
            "                key\n" +
            "                value\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }";

    public static void main(String[] args) {
        log.info("========================================");
        log.info("Skywalking Trace 查询工具");
        log.info("========================================");
        log.info("Endpoint: {}", SKYWALKING_ENDPOINT);
        log.info("Trace ID: {}", TEST_TRACE_ID);
        log.info("========================================");

        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper objectMapper = new ObjectMapper();

            // 构造 GraphQL 请求体
            Map<String, Object> variables = new HashMap<>();
            variables.put("traceId", TEST_TRACE_ID);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", QUERY_TRACE_QUERY);
            requestBody.put("variables", variables);

            // 构造 HTTP Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            log.info("正在查询 Trace ...");
            Map<String, Object> response = restTemplate.postForObject(
                    SKYWALKING_ENDPOINT,
                    requestEntity,
                    Map.class
            );

            // 解析结果
            if (response == null) {
                log.error("响应为空");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                log.error("响应中无 data 字段");
                log.error("完整响应: {}", response);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> queryTrace = (Map<String, Object>) data.get("queryTrace");
            if (queryTrace == null) {
                log.error("响应中无 queryTrace 字段");
                log.error("完整响应: {}", response);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> spansData = (List<Map<String, Object>>) queryTrace.get("spans");

            if (spansData == null || spansData.isEmpty()) {
                log.warn("未找到该 Trace ID 对应的链路数据");
                log.warn("请确认 Trace ID 是否正确: {}", TEST_TRACE_ID);
                return;
            }

            log.info("查询成功！共找到 {} 个 Span", spansData.size());
            log.info("========================================");

            // 转换为 SpanDto 并打印
            for (int i = 0; i < spansData.size(); i++) {
                Map<String, Object> spanMap = spansData.get(i);
                printSpan(i, spanMap);
            }

            log.info("========================================");
            log.info("查询完成");

        } catch (Exception e) {
            log.error("查询失败: {}", e.getMessage(), e);
        }
    }

    private static void printSpan(int index, Map<String, Object> spanMap) {
        log.info("--- Span {} ---", index);
        log.info("  traceId:      {}", spanMap.get("traceId"));
        log.info("  segmentId:    {}", spanMap.get("segmentId"));
        log.info("  spanId:       {}", spanMap.get("spanId"));
        log.info("  parentSpanId: {}", spanMap.get("parentSpanId"));
        log.info("  serviceCode:  {}", spanMap.get("serviceCode"));
        log.info("  endpointName: {}", spanMap.get("endpointName"));

        Long startTime = spanMap.get("startTime") != null ?
                Long.parseLong(spanMap.get("startTime").toString()) : null;
        Long endTime = spanMap.get("endTime") != null ?
                Long.parseLong(spanMap.get("endTime").toString()) : null;

        log.info("  startTime:    {}", formatTimestamp(startTime));
        log.info("  endTime:      {}", formatTimestamp(endTime));

        if (startTime != null && endTime != null) {
            log.info("  duration:     {}ms", endTime - startTime);
        }

        log.info("  component:    {}", spanMap.get("component"));
        log.info("  type:         {}", spanMap.get("type"));
        log.info("  peer:         {}", spanMap.get("peer"));
        log.info("  isError:      {}", spanMap.get("isError"));
        log.info("  layer:        {}", spanMap.get("layer"));

        // 打印 Tags
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tags = (List<Map<String, String>>) spanMap.get("tags");
        if (tags != null && !tags.isEmpty()) {
            log.info("  tags:");
            for (Map<String, String> tag : tags) {
                log.info("    - {}: {}", tag.get("key"), tag.get("value"));
            }
        }

        log.info("");
    }

    private static String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return "N/A";
        }
        return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}
