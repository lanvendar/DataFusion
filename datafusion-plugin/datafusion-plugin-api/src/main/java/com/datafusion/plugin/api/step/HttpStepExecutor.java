package com.datafusion.plugin.api.step;

import com.datafusion.plugin.api.cache.IntermediateCache;
import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.CacheConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FailurePolicyConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.PaginationConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.RequestConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ResponseConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.RetryConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.TimeoutConfig;
import com.datafusion.plugin.api.core.ApiExtractContext;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.expression.JmesPathEvaluator;
import com.datafusion.plugin.api.http.ApiHttpClient;
import com.datafusion.plugin.api.http.HttpRequestData;
import com.datafusion.plugin.api.http.HttpResponseData;
import com.datafusion.plugin.api.mapping.RecordMapper;
import com.datafusion.plugin.api.sink.SinkWriter;
import com.datafusion.plugin.api.template.TemplateResolver;
import com.datafusion.plugin.api.util.JsonUtils;
import com.datafusion.plugin.api.util.TextUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 步骤执行器,负责执行 HTTP 请求并处理响应.
 *
 * <p>
 * 支持分页、重试、缓存、字段映射等功能.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class HttpStepExecutor {
    
    /** HTTP 客户端实例. */
    private final ApiHttpClient httpClient;
    
    /** JMESPath 表达式求值器. */
    private final JmesPathEvaluator evaluator;
    
    /** 模板解析器. */
    private final TemplateResolver templateResolver;
    
    /** 记录映射器. */
    private final RecordMapper recordMapper;
    
    /** 中间缓存. */
    private final IntermediateCache cache;

    /**
     * 构造 HTTP 步骤执行器.
     *
     * @param httpClient HTTP 客户端
     * @param evaluator JMESPath 求值器
     * @param templateResolver 模板解析器
     * @param recordMapper 记录映射器
     * @param cache 中间缓存
     */
    public HttpStepExecutor(ApiHttpClient httpClient, JmesPathEvaluator evaluator, TemplateResolver templateResolver,
            RecordMapper recordMapper, IntermediateCache cache) {
        this.httpClient = httpClient;
        this.evaluator = evaluator;
        this.templateResolver = templateResolver;
        this.recordMapper = recordMapper;
        this.cache = cache;
    }

    /**
     * 执行 HTTP 步骤,处理分页、重试和缓存逻辑.
     *
     * @param context 抽取上下文
     * @param step 步骤配置
     * @param sinkWriter 数据写入器
     * @return 成功写入的记录数
     */
    public long execute(ApiExtractContext context, StepConfig step, SinkWriter sinkWriter) {
        PaginationConfig pagination = step.pagination == null ? new PaginationConfig() : step.pagination;
        String type = TextUtils.upper(pagination.type, "NONE");
        if ("NONE".equals(type)) {
            return executePage(context, step, sinkWriter, Map.of(), 0);
        }
        if ("PAGE".equals(type)) {
            return executePagePagination(context, step, sinkWriter, pagination);
        }
        if ("OFFSET".equals(type)) {
            return executeOffsetPagination(context, step, sinkWriter, pagination);
        }
        throw new ApiExtractException("Unsupported pagination.type: " + pagination.type);
    }

    private long executePagePagination(ApiExtractContext context, StepConfig step, SinkWriter sinkWriter,
            PaginationConfig pagination) {
        long total = 0;
        int maxPages = Math.max(1, pagination.maxPages);
        for (int page = pagination.startPage; page < pagination.startPage + maxPages; page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(pagination.pageParam, page);
            params.put(pagination.pageSizeParam, pagination.pageSize);
            long count = executePage(context, step, sinkWriter, params, pagination.pageSize);
            total += count;
            if (shouldStop(pagination.stopWhenEmpty, count, pagination.pageSize)) {
                break;
            }
        }
        return total;
    }

    private long executeOffsetPagination(ApiExtractContext context, StepConfig step, SinkWriter sinkWriter,
            PaginationConfig pagination) {
        long total = 0;
        int maxRequests = Math.max(1, pagination.maxRequests);
        for (int request = 0; request < maxRequests; request++) {
            int offset = pagination.startOffset + request * pagination.limit;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(pagination.offsetParam, offset);
            params.put(pagination.limitParam, pagination.limit);
            long count = executePage(context, step, sinkWriter, params, pagination.limit);
            total += count;
            if (shouldStop(pagination.stopWhenEmpty, count, pagination.limit)) {
                break;
            }
        }
        return total;
    }

    private boolean shouldStop(boolean stopWhenEmpty, long count, int pageSize) {
        if (stopWhenEmpty && count == 0) {
            return true;
        }
        return pageSize > 0 && count < pageSize;
    }

    private long executePage(ApiExtractContext context, StepConfig step, SinkWriter sinkWriter,
            Map<String, Object> pageParams, int expectedPageSize) {
        HttpResponseData response = executeWithRetry(context, step, pageParams);
        Object json = parseAndValidate(context, step, response);
        writeCache(context, step, json);
        writeStepOutput(context, step, json);
        List<Record> records = recordMapper.map(step.response, json, context);
        FailurePolicyConfig failurePolicy = failurePolicy(context, step.request);
        if (records.isEmpty() && "FAIL".equals(TextUtils.upper(failurePolicy.onEmptyData, "SUCCESS"))) {
            throw new ApiExtractException("Step returned empty data: " + step.id);
        }
        writeBatch(context, records, sinkWriter);
        return records.size();
    }

    private void writeBatch(ApiExtractContext context, List<Record> records, SinkWriter sinkWriter) {
        if (records.isEmpty()) {
            return;
        }
        int batchSize = context.getConfig().sink == null || context.getConfig().sink.write == null
                ? records.size() : Math.max(1, context.getConfig().sink.write.batchSize);
        for (int start = 0; start < records.size(); start += batchSize) {
            int end = Math.min(records.size(), start + batchSize);
            sinkWriter.write(records.subList(start, end));
        }
    }

    private HttpResponseData executeWithRetry(ApiExtractContext context, StepConfig step, Map<String, Object> pageParams) {
        RetryConfig retry = retry(context, step.request);
        int maxAttempts = Math.max(1, retry.maxAttempts);
        long interval = Math.max(0, retry.intervalMs);
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponseData response = httpClient.execute(buildRequest(context, step.request, pageParams));
                if (!retry.retryOnStatus.contains(response.getStatusCode()) || attempt == maxAttempts) {
                    return response;
                }
            } catch (IOException | InterruptedException e) {
                lastException = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt == maxAttempts) {
                    break;
                }
            }
            sleep(interval);
            interval = (long) (interval * Math.max(1.0, retry.backoffMultiplier));
        }
        throw new ApiExtractException("HTTP request failed after retry: " + step.id, lastException);
    }

    private HttpRequestData buildRequest(ApiExtractContext context, RequestConfig request, Map<String, Object> pageParams) {
        RequestConfig source = request == null ? new RequestConfig() : request;
        Map<String, Object> queryParams = new LinkedHashMap<>();
        if (source.queryParams != null) {
            queryParams.putAll(source.queryParams);
        }
        queryParams.putAll(pageParams);
        HttpRequestData target = new HttpRequestData();
        target.method = TextUtils.upper(source.method, "GET");
        target.url = appendQuery(templateResolver.resolve(source.url, context), queryParams, context);
        if (source.headers != null) {
            source.headers.forEach((key, value) -> target.headers.put(key, stringValue(templateResolver.resolveObject(value, context))));
        }
        target.body = body(source, context, target.headers);
        TimeoutConfig timeout = timeout(context, source);
        target.connectTimeoutMs = timeout.connectMs;
        target.readTimeoutMs = timeout.readMs;
        return target;
    }

    private String body(RequestConfig request, ApiExtractContext context, Map<String, String> headers) {
        String bodyType = TextUtils.upper(request.bodyType, "NONE");
        if ("NONE".equals(bodyType)) {
            return null;
        }
        if ("RAW".equals(bodyType)) {
            return templateResolver.resolve(request.rawBody == null ? String.valueOf(request.body) : request.rawBody, context);
        }
        Object body = templateResolver.resolveObject(request.body, context);
        if ("JSON".equals(bodyType)) {
            headers.putIfAbsent("Content-Type", "application/json");
            return JsonUtils.write(body);
        }
        if ("FORM".equals(bodyType) && body instanceof Map<?, ?> map) {
            headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
            List<String> items = new ArrayList<>();
            map.forEach((key, value) -> items.add(urlEncode(String.valueOf(key)) + "=" + urlEncode(stringValue(value))));
            return String.join("&", items);
        }
        throw new ApiExtractException("Unsupported request.bodyType: " + request.bodyType);
    }

    private Object parseAndValidate(ApiExtractContext context, StepConfig step, HttpResponseData response) {
        ResponseConfig responseConfig = step.response == null ? new ResponseConfig() : step.response;
        if (!responseConfig.successStatus.contains(response.getStatusCode())) {
            handleFailure(context, step.request, "HTTP status not successful: " + response.getStatusCode());
        }
        Object json;
        try {
            json = JsonUtils.MAPPER.readValue(response.getBody(), Object.class);
        } catch (Exception e) {
            FailurePolicyConfig failurePolicy = failurePolicy(context, step.request);
            if ("SUCCESS".equals(TextUtils.upper(failurePolicy.onParseError, "FAIL"))) {
                return Map.of();
            }
            throw new ApiExtractException("Failed to parse JSON response for step: " + step.id, e);
        }
        if (!TextUtils.isBlank(responseConfig.successExpression) && !evaluator.isTruthy(json, responseConfig.successExpression)) {
            Object message = TextUtils.isBlank(responseConfig.messageExpression) ? null
                    : evaluator.search(json, responseConfig.messageExpression);
            handleFailure(context, step.request, "Business success expression returned false: " + message);
        }
        return json;
    }

    private void handleFailure(ApiExtractContext context, RequestConfig request, String message) {
        FailurePolicyConfig failurePolicy = failurePolicy(context, request);
        if ("SUCCESS".equals(TextUtils.upper(failurePolicy.onHttpError, "FAIL"))) {
            return;
        }
        throw new ApiExtractException(message);
    }

    private void writeCache(ApiExtractContext context, StepConfig step, Object json) {
        CacheConfig cacheConfig = step.cache;
        if (cacheConfig == null || !cacheConfig.enabled) {
            return;
        }
        Object value = TextUtils.isBlank(cacheConfig.valueExpression) ? json : evaluator.search(json, cacheConfig.valueExpression);
        String prefix = context.getConfig().redis == null ? "" : context.getConfig().redis.keyPrefix + ":";
        String key = prefix + templateResolver.resolve(cacheConfig.key, context);
        long ttl = cacheConfig.ttlSeconds > 0 ? cacheConfig.ttlSeconds : context.getConfig().redis.ttlSeconds;
        cache.put(key, value, ttl, cacheConfig.mode);
    }

    private void writeStepOutput(ApiExtractContext context, StepConfig step, Object json) {
        if (step.output == null || step.output.isEmpty()) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        step.output.forEach((key, expression) -> output.put(key, evaluator.search(json, expression)));
        context.putStepOutput(step.id, output);
    }

    private TimeoutConfig timeout(ApiExtractContext context, RequestConfig request) {
        if (request != null && request.timeout != null) {
            return request.timeout;
        }
        ApiExtractJobConfig.RuntimeConfig runtime = context.getConfig().runtime;
        return runtime == null || runtime.timeout == null ? new TimeoutConfig() : runtime.timeout;
    }

    private RetryConfig retry(ApiExtractContext context, RequestConfig request) {
        if (request != null && request.retry != null) {
            return request.retry;
        }
        ApiExtractJobConfig.RuntimeConfig runtime = context.getConfig().runtime;
        return runtime == null || runtime.retry == null ? new RetryConfig() : runtime.retry;
    }

    private FailurePolicyConfig failurePolicy(ApiExtractContext context, RequestConfig request) {
        if (request != null && request.failurePolicy != null) {
            return request.failurePolicy;
        }
        ApiExtractJobConfig.RuntimeConfig runtime = context.getConfig().runtime;
        return runtime == null || runtime.failurePolicy == null ? new FailurePolicyConfig() : runtime.failurePolicy;
    }

    private String appendQuery(String url, Map<String, Object> queryParams, ApiExtractContext context) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        List<String> pairs = new ArrayList<>();
        queryParams.forEach((key, value) -> pairs.add(urlEncode(key) + "="
                + urlEncode(stringValue(templateResolver.resolveObject(value, context)))));
        return url + (url.contains("?") ? "&" : "?") + String.join("&", pairs);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiExtractException("Interrupted", e);
        }
    }
}
