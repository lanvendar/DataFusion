package com.datafusion.plugin.api.step;

import com.datafusion.plugin.api.cache.InMemoryIntermediateCache;
import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ResponseConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
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
        response.fields.add(field("today", "Data[].Today", true));
        response.fields.add(field("product_name", "Data[].Productname", false));
        response.fields.add(field("product_type_id_name", "Data[].ProducttypeIdName", false));
        response.fields.add(field("product_chn_name", "Data[].Productchnname", false));
        response.fields.add(field("specification", "Data[].Specification", false));
        response.fields.add(field("product_id_name", "Data[].ProductIdname", false));
        response.fields.add(field("product_detail_id_name", "Data[].ProductdetailIdname", false));
        response.fields.add(field("product_class", "Data[].Productclass", false));
        response.fields.add(field("price", "Data[].Price", false));
        response.fields.add(field("range_value", "Data[].Range", false));
        return response;
    }

    private FieldConfig field(String name, String expression, boolean key) {
        FieldConfig field = new FieldConfig();
        field.name = name;
        field.expression = expression;
        field.isKey = key;
        field.nullable = false;
        return field;
    }

    private static class CapturingHttpClient implements com.datafusion.plugin.api.http.ApiHttpClient {

        /**
         * Mock response body.
         */
        private final String responseBody;

        /**
         * Captured HTTP request.
         */
        private HttpRequestData request;

        CapturingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public HttpResponseData execute(HttpRequestData request) throws IOException, InterruptedException {
            this.request = request;
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
