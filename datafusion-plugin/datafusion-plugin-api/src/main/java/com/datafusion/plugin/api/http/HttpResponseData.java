package com.datafusion.plugin.api.http;

import java.util.List;
import java.util.Map;

/**
 * HTTP 响应数据封装类.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class HttpResponseData {
    
    /**
     * HTTP 状态码.
     */
    private final int statusCode;
    
    /**
     * 响应体内容.
     */
    private final String body;
    
    /**
     * 响应头映射.
     */
    private final Map<String, List<String>> headers;

    /**
     * 构造响应数据对象.
     *
     * @param statusCode HTTP 状态码
     * @param body 响应体内容
     * @param headers 响应头映射
     */
    public HttpResponseData(int statusCode, String body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
    }

    /**
     * 获取 HTTP 状态码.
     *
     * @return 状态码
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取响应体内容.
     *
     * @return 响应体字符串
     */
    public String getBody() {
        return body;
    }

    /**
     * 获取响应头映射.
     *
     * @return 响应头 Map,key 为头名称,value 为头值列表
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }
}
