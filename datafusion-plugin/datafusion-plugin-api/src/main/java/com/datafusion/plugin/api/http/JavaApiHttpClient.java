package com.datafusion.plugin.api.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDK HTTP 客户端实现.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class JavaApiHttpClient implements ApiHttpClient {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaApiHttpClient.class);

    @Override
    public HttpResponseData execute(HttpRequestData request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url))
                .timeout(Duration.ofMillis(request.readTimeoutMs));
        request.headers.forEach(builder::header);
        String method = request.method == null ? "GET" : request.method.toUpperCase();
        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(request.body == null ? "" : request.body));
        }
        long start = System.currentTimeMillis();
        LOGGER.info("HTTP 请求开始, method={}, url={}, connectTimeoutMs={}, readTimeoutMs={}",
                method, request.url, request.connectTimeoutMs, request.readTimeoutMs);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(request.connectTimeoutMs))
                .build();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        LOGGER.info("HTTP 请求完成, method={}, url={}, statusCode={}, elapsedMs={}",
                method, request.url, response.statusCode(), System.currentTimeMillis() - start);
        return new HttpResponseData(response.statusCode(), response.body(), response.headers().map());
    }
}
