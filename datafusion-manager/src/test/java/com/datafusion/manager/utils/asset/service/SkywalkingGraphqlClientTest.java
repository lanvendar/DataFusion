package com.datafusion.manager.utils.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.manager.asset.dto.skywalking.CallEdge;
import com.datafusion.manager.asset.dto.skywalking.SpanDto;
import com.datafusion.manager.asset.service.SkywalkingGraphqlClient;
import com.datafusion.manager.asset.service.TraceGraphBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SkywalkingGraphqlClient 测试类
 *
 * 使用方法：直接修改 TEST_TRACE_ID 的值，运行 testQueryTrace 方法即可查看返回结果
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/22
 * @since 2025/10/22
 */
@Slf4j
public class SkywalkingGraphqlClientTest {

    /**
     * Skywalking GraphQL endpoint 地址
     * 请根据实际环境修改
     */
    private static final String SKYWALKING_ENDPOINT = "https://skywalking.we.goodwe.com/graphql";

    /**
     * 在这里修改你要查询的 traceId
     */
    private static final String TEST_TRACE_ID = "a1ed526926c94ed29146e1616619ed53.151.17694170447864375";

    private SkywalkingGraphqlClient skywalkingGraphqlClient;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        skywalkingGraphqlClient = new SkywalkingGraphqlClient(restTemplate, SKYWALKING_ENDPOINT, new ObjectMapper());
    }

    /**
     * 测试查询单个 Trace ID 对应的完整链路 Span 详情
     *
     * 使用方法：
     * 1. 修改 TEST_TRACE_ID 为实际的 traceId
     * 2. 根据你的 Skywalking 环境修改 SKYWALKING_ENDPOINT
     * 3. 运行此测试方法即可查看返回结果
     */
    @Test
    @DisplayName("查询 Trace 详情 - 直接运行查看结果")
    void testQueryTrace() {
        String traceId = TEST_TRACE_ID;

        log.info("========================================");
        log.info("开始查询 Trace: {}", traceId);
        log.info("Endpoint: {}", SKYWALKING_ENDPOINT);
        log.info("========================================");

        // 执行测试
        List<SpanDto> spans = skywalkingGraphqlClient.queryTrace(traceId);
        // 查询Trace详情并生成CallEdge
        Set<CallEdge> callEdges = new HashSet<>();

        if (CollectionUtil.isNotEmpty(spans)) {
            TraceGraphBuilder traceGraphBuilder = new TraceGraphBuilder();
            Set<CallEdge> edges = traceGraphBuilder.buildCallGraph(spans);
            callEdges.addAll(edges);
        }

        // 验证结果
        assertNotNull(spans, "返回结果不应为 null");

        // 打印结果到日志
        log.info("========================================");
        log.info("查询完成！");
        log.info("Trace ID: {}", traceId);
        log.info("返回 Span 数量: {}", spans.size());
        log.info("========================================");

        if (spans.isEmpty()) {
            log.warn("未找到该 Trace ID 对应的链路数据，请确认 Trace ID 是否正确");
            return;
        }

        for (int i = 0; i < spans.size(); i++) {
            SpanDto span = spans.get(i);
            log.info("--- Span {} ---", i);
            log.info("traceId:      {}", span.getTraceId());
            log.info("segmentId:    {}", span.getSegmentId());
            log.info("spanId:       {}", span.getSpanId());
            log.info("parentSpanId: {}", span.getParentSpanId());
            log.info("serviceCode:  {}", span.getServiceCode());
            log.info("endpointName: {}", span.getEndpointName());
            log.info("startTime:    {}", span.getStartTime());
            log.info("endTime:      {}", span.getEndTime());
            if (span.getStartTime() != null && span.getEndTime() != null) {
                log.info("duration:     {}ms", span.getEndTime() - span.getStartTime());
            }
            log.info("component:    {}", span.getComponent());
            log.info("type:         {}", span.getType());
            log.info("peer:         {}", span.getPeer());
            log.info("isError:      {}", span.getIsError());
            log.info("layer:        {}", span.getLayer());
            if (span.getTags() != null && !span.getTags().isEmpty()) {
                log.info("tags:");
                span.getTags().forEach(tag -> log.info("  - {}: {}", tag.getKey(), tag.getValue()));
            }
            log.info("");
        }

        log.info("========================================");
    }

}
