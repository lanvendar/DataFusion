package com.datafusion.plugin.api.http;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 请求数据封装类.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class HttpRequestData {
    
    /**
     * HTTP 请求方法(GET/POST/PUT/DELETE).
     */
    public String method;
    
    /**
     * 请求 URL.
     */
    public String url;
    
    /**
     * 请求头映射.
     */
    public Map<String, String> headers = new LinkedHashMap<>();
    
    /**
     * 请求体内容.
     */
    public String body;
    
    /**
     * 连接超时时间(毫秒).
     */
    public int connectTimeoutMs;
    
    /**
     * 读取超时时间(毫秒).
     */
    public int readTimeoutMs;
}
