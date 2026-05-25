package com.datafusion.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 连接与客户端参数（对应配置前缀 {@code oss}）.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/14
 * @since 2026/5/14
 */
@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /**
     * 是否启用 OSS 客户端 Bean；false 时不创建 {@link com.aliyun.oss.OSS}，便于本地无密钥启动.
     */
    private boolean enabled = false;

    /**
     * Endpoint，如 https://oss-cn-hangzhou.aliyuncs.com.
     */
    private String endpoint;

    /**
     * AccessKeyId.
     */
    private String accessKeyId;

    /**
     * AccessKeySecret.
     */
    private String accessKeySecret;

    /**
     * STS 临时凭证 securityToken；非 STS 场景留空.
     */
    private String securityToken;

    /**
     * 默认 Bucket；方法入参未传 bucket 时使用.
     */
    private String bucketName;

    /**
     * 是否支持 CNAME（自定义域名）.
     */
    private boolean cnameEnabled = false;

    /**
     * 连接超时（毫秒）.
     */
    private int connectionTimeoutMs = 5000;

    /**
     * Socket 读超时（毫秒）.
     */
    private int socketTimeoutMs = 50000;

    /**
     * 请求失败重试次数.
     */
    private int maxErrorRetry = 3;
}
