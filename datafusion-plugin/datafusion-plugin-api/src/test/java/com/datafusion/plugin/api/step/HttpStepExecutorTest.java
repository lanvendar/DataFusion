package com.datafusion.plugin.api.step;

import com.datafusion.plugin.api.cache.InMemoryIntermediateCache;
import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ResponseConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ValueExpressionConfig;
import com.datafusion.plugin.api.core.ApiExtractContext;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.expression.JmesPathEvaluator;
import com.datafusion.plugin.api.http.HttpRequestData;
import com.datafusion.plugin.api.http.HttpResponseData;
import com.datafusion.plugin.api.mapping.RecordMapper;
import com.datafusion.plugin.api.sink.SinkWriter;
import com.datafusion.plugin.api.template.TemplateResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP step executor unit tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class HttpStepExecutorTest {

    /**
     * Benchmark price API response sample.
     */
    private static final String BENCHMARK_RESPONSE = """
            {
                "Success": true,
                "Message": "基准价获取成功",
                "Data": [
                    {
                        "Today": "05/21/2026 13:30:07",
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
                        "Today": "05/21/2026 13:30:07",
                        "Productname": "DTY",
                        "ProducttypeIdName": "PET(半消光)",
                        "Productchnname": "空",
                        "Specification": "174dtex288-ED20L",
                        "ProductIdname": "1108",
                        "ProductdetailIdname": "M00Y",
                        "Productclass": "A",
                        "Price": 8700.0000,
                        "Range": 50.0000
                    }
                ]
            }
            """;

    /**
     * Verifies benchmark response array mapping and sink write behavior.
     *
     * @throws Exception when executor fails unexpectedly
     */
    @Test
    public void executeShouldMapBenchmarkResponseDataArray() throws Exception {
        ApiExtractJobConfig config = benchmarkConfig();
        StepConfig step = config.steps.get(0);
        CapturingHttpClient httpClient = new CapturingHttpClient(BENCHMARK_RESPONSE);
        CapturingSinkWriter sinkWriter = new CapturingSinkWriter();
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        HttpStepExecutor executor = new HttpStepExecutor(httpClient, evaluator, templateResolver,
                new RecordMapper(evaluator, templateResolver), cache);

        long records = executor.execute(new ApiExtractContext(config, "benchmark-test-run"), step, sinkWriter);

        Assertions.assertEquals(2, records);
        Assertions.assertEquals("GET", httpClient.request.method);
        Assertions.assertEquals("http://172.30.19.4:9232/api/product/getBenchmark", httpClient.request.url);
        Assertions.assertEquals(2, sinkWriter.records.size());
        Assertions.assertEquals("B000", sinkWriter.records.get(0).get("product_detail_id_name"));
        Assertions.assertEquals("M00Y", sinkWriter.records.get(1).get("product_detail_id_name"));
        Assertions.assertEquals(9550.0, ((Number) sinkWriter.records.get(0).get("price")).doubleValue());
        Assertions.assertEquals(50.0, ((Number) sinkWriter.records.get(1).get("range_value")).doubleValue());
    }

    @Test
    public void executeShouldAllowMissingNullableField() throws Exception {
        ApiExtractJobConfig config = benchmarkConfig();
        CapturingHttpClient httpClient = new CapturingHttpClient(missingProductDetailResponse());
        CapturingSinkWriter sinkWriter = new CapturingSinkWriter();
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        HttpStepExecutor executor = new HttpStepExecutor(httpClient, evaluator, templateResolver,
                new RecordMapper(evaluator, templateResolver), cache);

        long records = executor.execute(new ApiExtractContext(config, "missing-required-field-test-run"),
                config.steps.get(0), sinkWriter);

        Assertions.assertEquals(1, records);
        Assertions.assertNull(sinkWriter.records.get(0).get("product_detail_id_name"));
    }

    @Test
    public void executeShouldApplyPagePaginationAndStopOnShortPage() {
        ApiExtractJobConfig config = benchmarkConfig();
        StepConfig step = config.steps.get(0);
        step.pagination.type = "PAGE";
        step.pagination.pageParam = "page";
        step.pagination.pageSizeParam = "pageSize";
        step.pagination.startPage = 1;
        step.pagination.pageSize = 2;
        step.pagination.maxPages = 5;
        CapturingHttpClient httpClient = new CapturingHttpClient(BENCHMARK_RESPONSE, shortPageResponse());
        CapturingSinkWriter sinkWriter = new CapturingSinkWriter();
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        HttpStepExecutor executor = new HttpStepExecutor(httpClient, evaluator, templateResolver,
                new RecordMapper(evaluator, templateResolver), cache);

        long records = executor.execute(new ApiExtractContext(config, "page-test-run"), step, sinkWriter);

        Assertions.assertEquals(3, records);
        Assertions.assertEquals(2, httpClient.requests.size());
        Assertions.assertTrue(httpClient.requests.get(0).url.contains("page=1"));
        Assertions.assertTrue(httpClient.requests.get(0).url.contains("pageSize=2"));
        Assertions.assertTrue(httpClient.requests.get(1).url.contains("page=2"));
    }

    @Test
    public void executeShouldApplyOffsetPaginationAndStopOnEmptyPage() {
        ApiExtractJobConfig config = benchmarkConfig();
        StepConfig step = config.steps.get(0);
        step.pagination.type = "OFFSET";
        step.pagination.offsetParam = "offset";
        step.pagination.limitParam = "limit";
        step.pagination.startOffset = 0;
        step.pagination.limit = 2;
        step.pagination.maxRequests = 5;
        CapturingHttpClient httpClient = new CapturingHttpClient(BENCHMARK_RESPONSE, emptyPageResponse());
        CapturingSinkWriter sinkWriter = new CapturingSinkWriter();
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        HttpStepExecutor executor = new HttpStepExecutor(httpClient, evaluator, templateResolver,
                new RecordMapper(evaluator, templateResolver), cache);

        long records = executor.execute(new ApiExtractContext(config, "offset-test-run"), step, sinkWriter);

        Assertions.assertEquals(2, records);
        Assertions.assertEquals(2, httpClient.requests.size());
        Assertions.assertTrue(httpClient.requests.get(0).url.contains("offset=0"));
        Assertions.assertTrue(httpClient.requests.get(1).url.contains("offset=2"));
    }

    @Test
    public void executeShouldRetryRetryableStatus() {
        ApiExtractJobConfig config = benchmarkConfig();
        config.httpConfig.maxAttempts = 2;
        config.httpConfig.retryIntervalMs = 0;
        StepConfig step = config.steps.get(0);
        CapturingHttpClient httpClient = new CapturingHttpClient(
                new HttpResponseData(500, "{\"Success\":false}", Collections.emptyMap()),
                new HttpResponseData(200, BENCHMARK_RESPONSE, Collections.emptyMap()));
        CapturingSinkWriter sinkWriter = new CapturingSinkWriter();
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        HttpStepExecutor executor = new HttpStepExecutor(httpClient, evaluator, templateResolver,
                new RecordMapper(evaluator, templateResolver), cache);

        long records = executor.execute(new ApiExtractContext(config, "retry-test-run"), step, sinkWriter);

        Assertions.assertEquals(2, records);
        Assertions.assertEquals(2, httpClient.requests.size());
    }

    @Test
    public void executeShouldWriteCacheWithConfiguredMode() {
        ApiExtractJobConfig config = benchmarkConfig();
        config.redis.enabled = true;
        config.redis.options.put("keyPrefix", "prefix");
        StepConfig step = config.steps.get(0);
        step.redisCache.enabled = true;
        step.redisCache.key = "bench:${job.id}";
        step.redisCache.loadMode = "HASH";
        ValueExpressionConfig productDetail = new ValueExpressionConfig();
        productDetail.name = "ProductdetailIdname";
        productDetail.expression = "Data[0].ProductdetailIdname";
        step.redisCache.valueExpressions.add(productDetail);
        CapturingHttpClient httpClient = new CapturingHttpClient(BENCHMARK_RESPONSE);
        CapturingSinkWriter sinkWriter = new CapturingSinkWriter();
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        JmesPathEvaluator evaluator = new JmesPathEvaluator();
        TemplateResolver templateResolver = new TemplateResolver(cache);
        HttpStepExecutor executor = new HttpStepExecutor(httpClient, evaluator, templateResolver,
                new RecordMapper(evaluator, templateResolver), cache);

        executor.execute(new ApiExtractContext(config, "cache-test-run"), step, sinkWriter);

        Object value = cache.get("prefix:bench:benchmark-price-api");
        Assertions.assertInstanceOf(Map.class, value);
        Assertions.assertEquals("B000", ((Map<?, ?>) value).get("ProductdetailIdname"));
    }

    private ApiExtractJobConfig benchmarkConfig() {
        ApiExtractJobConfig config = new ApiExtractJobConfig();
        config.job.id = "benchmark-price-api";
        config.sink.type = "NOOP";
        config.sink.write.batchSize = 100;

        StepConfig step = new StepConfig();
        step.id = "getBenchmark";
        step.request.method = "GET";
        step.request.url = "http://172.30.19.4:9232/api/product/getBenchmark";
        step.response = benchmarkResponseConfig();
        config.steps.add(step);
        return config;
    }

    private ResponseConfig benchmarkResponseConfig() {
        ResponseConfig response = new ResponseConfig();
        response.successExpression = "Success";
        response.messageExpression = "Message";
        response.recordMode = "ARRAY";
        response.fields.add(field("today", "Data[].Today"));
        response.fields.add(field("product_name", "Data[].Productname"));
        response.fields.add(field("product_type_id_name", "Data[].ProducttypeIdName"));
        response.fields.add(field("product_chn_name", "Data[].Productchnname"));
        response.fields.add(field("specification", "Data[].Specification"));
        response.fields.add(field("product_id_name", "Data[].ProductIdname"));
        response.fields.add(field("product_detail_id_name", "Data[].ProductdetailIdname"));
        response.fields.add(field("product_class", "Data[].Productclass"));
        response.fields.add(field("price", "Data[].Price"));
        response.fields.add(field("range_value", "Data[].Range"));
        return response;
    }

    private String shortPageResponse() {
        return """
                {
                    "Success": true,
                    "Message": "基准价获取成功",
                    "Data": [
                        {
                            "Today": "05/21/2026 13:30:07",
                            "Productname": "DTY",
                            "ProducttypeIdName": "PET(半消光)",
                            "Productchnname": "空",
                            "Specification": "174dtex288-ED20L",
                            "ProductIdname": "1108",
                            "ProductdetailIdname": "LAST",
                            "Productclass": "A",
                            "Price": 8000.0000,
                            "Range": 10.0000
                        }
                    ]
                }
                """;
    }

    private String missingProductDetailResponse() {
        return """
                {
                    "Success": true,
                    "Message": "基准价获取成功",
                    "Data": [
                        {
                            "Today": "05/21/2026 13:30:07",
                            "Productname": "DTY",
                            "ProducttypeIdName": "PET(半消光)",
                            "Productchnname": "空",
                            "Specification": "226dtex384-ED20L",
                            "ProductIdname": "1110",
                            "Productclass": "A",
                            "Price": 7850.0000,
                            "Range": 0.0000
                        }
                    ]
                }
                """;
    }

    private String emptyPageResponse() {
        return """
                {
                    "Success": true,
                    "Message": "基准价获取成功",
                    "Data": []
                }
                """;
    }

    private FieldConfig field(String name, String expression) {
        FieldConfig field = new FieldConfig();
        field.name = name;
        field.expression = expression;
        return field;
    }

    private static class CapturingHttpClient implements com.datafusion.plugin.api.http.ApiHttpClient {

        /**
         * Mock response body.
         */
        private final String responseBody;
        private final List<HttpResponseData> responses;

        /**
         * Captured HTTP request.
         */
        private HttpRequestData request;

        private final List<HttpRequestData> requests = new ArrayList<>();

        CapturingHttpClient(String responseBody) {
            this.responseBody = responseBody;
            this.responses = new ArrayList<>();
        }

        CapturingHttpClient(String firstResponseBody, String secondResponseBody) {
            this.responseBody = null;
            this.responses = new ArrayList<>(List.of(
                    new HttpResponseData(200, firstResponseBody, Collections.emptyMap()),
                    new HttpResponseData(200, secondResponseBody, Collections.emptyMap())));
        }

        CapturingHttpClient(HttpResponseData firstResponse, HttpResponseData secondResponse) {
            this.responseBody = null;
            this.responses = new ArrayList<>(List.of(firstResponse, secondResponse));
        }

        @Override
        public HttpResponseData execute(HttpRequestData request) throws IOException, InterruptedException {
            this.request = request;
            this.requests.add(request);
            if (!responses.isEmpty()) {
                return responses.remove(0);
            }
            return new HttpResponseData(200, responseBody, Collections.emptyMap());
        }
    }

    private static class CapturingSinkWriter implements SinkWriter {

        /**
         * Captured written records.
         */
        private final List<Record> records = new ArrayList<>();

        @Override
        public void open(ApiExtractJobConfig.SinkConfig sink) {
        }

        @Override
        public void write(List<Record> records) {
            this.records.addAll(records);
        }

        @Override
        public void flush() {
        }
    }
}
