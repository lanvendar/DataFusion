package com.datafusion.manager.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端单例（Spring {@code @Bean} 默认 singleton），关闭时由 {@code destroyMethod} 释放连接.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/14
 * @since 2026/5/14
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssClientConfiguration {

    /**
     * 创建 OSS 客户端.
     *
     * @param props 配置
     * @return OSS 实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "true")
    public OSS ossClient(OssProperties props) {
        if (StringUtils.isBlank(props.getEndpoint())) {
            throw new IllegalStateException("oss.enabled=true 时 oss.endpoint 不能为空");
        }
        if (StringUtils.isBlank(props.getAccessKeyId()) || StringUtils.isBlank(props.getAccessKeySecret())) {
            throw new IllegalStateException("oss.enabled=true 时 oss.access-key-id / oss.access-key-secret 不能为空");
        }
        ClientBuilderConfiguration clientConfiguration = new ClientBuilderConfiguration();
        clientConfiguration.setConnectionTimeout(props.getConnectionTimeoutMs());
        clientConfiguration.setSocketTimeout(props.getSocketTimeoutMs());
        clientConfiguration.setMaxErrorRetry(props.getMaxErrorRetry());
        clientConfiguration.setSupportCname(props.isCnameEnabled());
        String endpoint = props.getEndpoint().trim();
        String accessKeyId = props.getAccessKeyId().trim();
        String accessKeySecret = props.getAccessKeySecret().trim();
        if (StringUtils.isNotBlank(props.getSecurityToken())) {
            return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret,
                    props.getSecurityToken().trim(), clientConfiguration);
        }
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret, clientConfiguration);
    }
}
