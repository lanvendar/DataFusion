package com.datafusion.plugin.api.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class JavaApiHttpClient implements ApiHttpClient {

    @Override
    public HttpResponseData execute(HttpRequestData request) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(request.connectTimeoutMs))
                .build();
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
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpResponseData(response.statusCode(), response.body(), response.headers().map());
    }
}
