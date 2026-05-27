package com.datafusion.plugin.api.http;

import java.io.IOException;

/**
 * HTTP 客户端接口,用于发送 API 请求并接收响应.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ApiHttpClient {
    
    /**
     * 执行 HTTP 请求.
     *
     * @param request 请求数据,包含 URL、方法、头信息和请求体
     * @return 响应数据,包含状态码、响应体和响应头
     * @throws IOException IO 异常
     * @throws InterruptedException 中断异常
     */
    HttpResponseData execute(HttpRequestData request) throws IOException, InterruptedException;
}
