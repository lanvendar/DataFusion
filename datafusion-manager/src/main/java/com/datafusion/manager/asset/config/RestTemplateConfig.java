package com.datafusion.manager.asset.config;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

/**
 * RestTemplateConfig . 
 * @author xufeng
 * @version 1.0.0, 2026/4/3
 * @since 2026/4/3
 */
@Configuration
public class RestTemplateConfig {

    @Bean("skywalkingRestTemplate")
    public RestTemplate restTemplate() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        // 1. 创建一个信任所有证书的 SSLContext
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, (chain, authType) -> true) // 信任所有
                .build();

        // 2. 创建忽略域名校验的连接工厂
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(
                sslContext,
                NoopHostnameVerifier.INSTANCE);

        // 3. 构建 HttpClient
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();

        // 4. 将 HttpClient 注入到 RestTemplate 的工厂中
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);

        // 设置超时时间（可选建议）
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        return new RestTemplate(requestFactory);
    }
}
