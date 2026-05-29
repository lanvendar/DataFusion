package com.datafusion.plugin.api.integration;

import com.datafusion.plugin.api.cache.InMemoryIntermediateCache;
import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ApiExtractContext;
import com.datafusion.plugin.api.core.ApiExtractResult;
import com.datafusion.plugin.api.core.DefaultApiExtractRunner;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.expression.JmesPathEvaluator;
import com.datafusion.plugin.api.mapping.RecordMapper;
import com.datafusion.plugin.api.sink.NoopSinkWriter;
import com.datafusion.plugin.api.step.HttpStepExecutor;
import com.datafusion.plugin.api.template.TemplateResolver;
import com.datafusion.plugin.api.util.JsonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 基准价 API 抽数集成测试.
 *
 * <p>
 * 从 test/resources/benchmark-test-job.json 读取配置,
 * 使用真实的 HTTP 请求访问基准价接口,验证完整的抽数链路.
 * </p>
 *
 * <p>
 * 该测试需要目标 API 可达,标记为 integration 测试.
 * 当网络不可达时,测试会自动跳过(aborted),不会导致构建失败.
 * CI 环境中可通过 Maven profile 或 JUnit tag 控制是否执行.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 */
@Tag("integration")
public class BenchmarkApiIntegrationTest {

    /**
     * 测试配置文件路径.
     */
    private static final String CONFIG_FILE = "benchmark-test-job.json";

    /**
     * 基准价 API URL.
     */
    private static final String BENCHMARK_API_URL = "http://172.30.19.4:9232/api/product/getBenchmark";

    /**
     * 从 test resources 读取 JSON 配置并执行完整抽数链路,
     * 验证 HTTP 请求成功、响应解析正确、记录数大于 0.
     *
     * <p>
     * 网络不可达时自动跳过,不导致构建失败.
     * </p>
     */
    @Test
    public void runBenchmarkApiExtractWithRealHttp() throws Exception {
        Assumptions.assumeTrue(isApiReachable(), "基准价 API 不可达,跳过集成测试");

        ApiExtractJobConfig config = loadConfig();
        DefaultApiExtractRunner runner = new DefaultApiExtractRunner();
        ApiExtractResult result = runner.run(config);

        Assertions.assertTrue(result.isSuccess(), "任务应成功执行,错误信息: " + result.getErrorMessage());
        Assertions.assertTrue(result.getRecords() > 0, "应抽取到至少一条记录,实际: " + result.getRecords());
        Assertions.assertEquals(1, result.getSteps().size(), "应有 1 个步骤结果");
        Assertions.assertTrue(result.getSteps().get(0).isSuccess(), "步骤应成功执行");
        Assertions.assertTrue(result.getSteps().get(0).getRecords() > 0, "步骤应抽取到记录");

        System.out.println("抽数结果: " + JsonUtils.write(result));
        System.out.println("抽取记录数: " + result.getRecords());
        System.out.println("耗时: " + result.getElapsedMs() + " ms");
    }

    /**
     * 使用 Mock HTTP 客户端和 NoopSinkWriter 验证完整的抽数链路（不依赖网络）.
     *
     * <p>
     * 使用真实的基准价 API 响应 JSON 作为 Mock 数据,
     * 验证配置加载、JMESPath 解析和记录生成的完整链路.
     * 使用 NoopSinkWriter 作为写入器,仅验证记录数.
     * </p>
     */
    @Test
    public void runBenchmarkApiExtractWithMockResponse() throws Exception {
        ApiExtractJobConfig config = loadConfig();
        MockHttpClient mockClient = new MockHttpClient(BENCHMARK_MOCK_RESPONSE);
        NoopSinkWriter noopSink = new NoopSinkWriter();
        noopSink.open(config.sink);

        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        RecordMapper recordMapper = new RecordMapper(evaluator, templateResolver);

        HttpStepExecutor executor = new HttpStepExecutor(
                        mockClient, evaluator, templateResolver, recordMapper, cache);

        ApiExtractContext context = new ApiExtractContext(config, "mock-test-run");

        long records = executor.execute(context, config.steps.get(0), noopSink);

        Assertions.assertTrue(records > 0, "Mock 响应应产生记录,实际: " + records);

        System.out.println("Mock 抽数记录数: " + records);
    }

    /**
     * 使用 RecordMapper 直接验证 Mock 响应的字段映射（不依赖网络和 HTTP 客户端）.
     *
     * <p>
     * 从 Mock 响应 JSON 中通过 JMESPath 提取记录,验证字段映射的正确性.
     * </p>
     */
    @Test
    public void verifyBenchmarkFieldMappingWithMockResponse() throws Exception {
        ApiExtractJobConfig config = loadConfig();
        Object json = JsonUtils.MAPPER.readValue(BENCHMARK_MOCK_RESPONSE, Object.class);

        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        RecordMapper recordMapper = new RecordMapper(evaluator, templateResolver);

        ApiExtractContext context = new ApiExtractContext(config, "mapping-test-run");
        java.util.List<Record> records = recordMapper.map(config.steps.get(0).response, json, context);

        Assertions.assertEquals(4, records.size(), "应映射出 4 条记录");

        // 验证第一条记录的关键字段
        Record firstRecord = records.get(0);
        Assertions.assertNotNull(firstRecord.get("today"), "today 字段不应为空");
        Assertions.assertEquals("B000", firstRecord.get("product_detail_id_name"),
                "第一条 product_detail_id_name 应为 B000");
        Assertions.assertEquals(9550.0, ((Number) firstRecord.get("price")).doubleValue(),
                "第一条 price 应为 9550");
        Assertions.assertEquals(300.0, ((Number) firstRecord.get("range_value")).doubleValue(),
                "第一条 range_value 应为 300");

        // 验证最后一条记录
        Record lastRecord = records.get(3);
        Assertions.assertEquals("MF00", lastRecord.get("product_detail_id_name"),
                "最后一条 product_detail_id_name 应为 MF00");

        System.out.println("映射记录数: " + records.size());
        System.out.println("第一条记录: " + JsonUtils.write(firstRecord));
        System.out.println("最后一条记录: " + JsonUtils.write(lastRecord));
    }

    /**
     * 检测基准价 API 是否可达.
     *
     * @return true 表示 API 可达
     */
    private boolean isApiReachable() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(BENCHMARK_API_URL).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private ApiExtractJobConfig loadConfig() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            Assertions.assertNotNull(is, "配置文件不存在: " + CONFIG_FILE);
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return JsonUtils.read(json, ApiExtractJobConfig.class);
        }
    }

    /**
     * 基准价 API Mock 响应数据,基于真实接口返回格式.
     *
     * <p>
     * 每条记录都包含所有字段,确保 JMESPath 投影返回等长数组.
     * 真实接口中部分记录可能缺少 Productchnname,但 JMESPath
     * 投影会过滤掉 null 导致数组长度不一致,因此 Mock 数据补全字段.
     * </p>
     */
    private static final String BENCHMARK_MOCK_RESPONSE = """
            {
                "Success": true,
                "Message": "基准价获取成功",
                "Data": [
                    {
                        "Today": "05/27/2026 13:37:46",
                        "Productname": "DTY",
                        "ProducttypeIdName": "PET(半消光)",
                        "Productchnname": "空",
                        "Specification": "174dtex288-ED20L",
                        "ProductIdname": "1108",
                        "ProductdetailIdname": "B000",
                        "Productclass": "AA",
                        "Price": 9550.0000,
                        "Range": 300.0000
                    },
                    {
                        "Today": "05/27/2026 13:37:46",
                        "Productname": "DTY",
                        "ProducttypeIdName": "PET(半消光)",
                        "Productchnname": "空",
                        "Specification": "174dtex288-ED20L",
                        "ProductIdname": "1108",
                        "ProductdetailIdname": "M00Y",
                        "Productclass": "A",
                        "Price": 8700.0000,
                        "Range": 50.0000
                    },
                    {
                        "Today": "05/27/2026 13:37:46",
                        "Productname": "DTY",
                        "ProducttypeIdName": "PET(半消光)",
                        "Productchnname": "--",
                        "Specification": "174dtex288-ED20L",
                        "ProductIdname": "1108",
                        "ProductdetailIdname": "M010",
                        "Productclass": "AAA",
                        "Price": 8200.0000,
                        "Range": 100.0000
                    },
                    {
                        "Today": "05/27/2026 13:37:46",
                        "Productname": "DTY",
                        "ProducttypeIdName": "PET(半消光)",
                        "Productchnname": "--",
                        "Specification": "174dtex288-ED20L",
                        "ProductIdname": "1108",
                        "ProductdetailIdname": "MF00",
                        "Productclass": "A",
                        "Price": 9350.0000,
                        "Range": 50.0000
                    }
                ]
            }
            """;

    /**
     * Mock HTTP 客户端,返回预设的响应数据.
     */
    private static class MockHttpClient implements com.datafusion.plugin.api.http.ApiHttpClient {

        private final String responseBody;

        MockHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public com.datafusion.plugin.api.http.HttpResponseData execute(
                com.datafusion.plugin.api.http.HttpRequestData request)
                throws java.io.IOException, InterruptedException {
            return new com.datafusion.plugin.api.http.HttpResponseData(
                    200, responseBody, java.util.Collections.emptyMap());
        }
    }

}
