package com.datafusion.manager.auth;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.auth.dto.FeignAuthentication;
import com.datafusion.manager.auth.dto.UserPrincipleNoAuth;
import com.datafusion.manager.utils.HttpUtils;
import feign.RequestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SecurityInterceptor.
 * Feign拦截器，用于将安全上下文传播到下游服务。
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/8
 * @since 2025/9/8
 */
@Component
public class SecurityInterceptor {
    /**
     * Redis存储Token的Key前缀.
     */
    public static final String TOKEN_APP_REDIS_KEY = "goodwe:sebu:secp:sso:token:app:";
    
    /**
     * Redis存储Token的Key前缀.
     */
    public static final String TOKEN_WEB_REDIS_KEY = "goodwe:sebu:secp:sso:token:web:";
    
    /**
     * RedisTemplate.
     */
    private final RedisTemplate<Object, Object> tokenRedisTemplate;
    
    /**
     * 构造函数，注入RedisTemplate.
     *
     * @param tokenRedisTemplate RedisTemplate实例.
     */
    public SecurityInterceptor(RedisTemplate<Object, Object> tokenRedisTemplate) {
        this.tokenRedisTemplate = tokenRedisTemplate;
    }
    
    /**
     * 将安全头信息应用到发出的Feign请求中.
     *
     * @param requestTemplate 待发送请求的模板.
     */
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(requestAttributes)) {
            return;
        }
        HttpServletRequest request = requestAttributes.getRequest();
        
        // 1. 从 FeignContextHolder 传播上下文（主要用于异步或新线程场景）
        propagateFromFeignContext(requestTemplate);
        
        // 2. 从原始HTTP请求传播标准Header
        propagateHeaders(requestTemplate, request);
        
        // 3. 确保用户信息Header存在，如果不存在则通过Token解析
        ensureUserHeaders(requestTemplate, request);
    }
    
    /**
     * 从FeignContextHolder传播Header。这对于异步操作至关重要，因为在异步线程中RequestContextHolder可能不可用.
     *
     * @param requestTemplate 待发送请求的模板.
     */
    private void propagateFromFeignContext(RequestTemplate requestTemplate) {
        FeignAuthentication feignAuth = FeignContextHolder.getFeignAuthentication();
        if (Objects.nonNull(feignAuth)) {
            requestTemplate.header(HttpUtils.TOKEN_HEADER, feignAuth.getToken());
            if (CharSequenceUtil.isNotBlank(feignAuth.getUuid())) {
                requestTemplate.header(HttpUtils.HEADER_UUID, feignAuth.getUuid());
            }
            if (CharSequenceUtil.isNotBlank(feignAuth.getAppVersion())) {
                requestTemplate.header(HttpUtils.HEADER_APP_VERSION, feignAuth.getAppVersion());
            }
            if (CharSequenceUtil.isNotBlank(feignAuth.getPageKey())) {
                requestTemplate.header(HttpUtils.HEADER_PAGE_KEY, feignAuth.getPageKey());
            }
            if (CharSequenceUtil.isNotBlank(feignAuth.getProjectName())) {
                requestTemplate.header(HttpUtils.HEADER_PROJECT_NAME, feignAuth.getProjectName());
            }
        }
    }
    
    /**
     * 从进入的请求向发出的Feign请求传播标准Header.
     *
     * @param requestTemplate 待发送请求的模板.
     * @param request         待发送请求的HTTP请求.
     */
    private void propagateHeaders(RequestTemplate requestTemplate, HttpServletRequest request) {
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_PAGE_KEY, request.getHeader(HttpUtils.HEADER_PAGE_KEY));
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_PROJECT_NAME, request.getHeader(HttpUtils.HEADER_PROJECT_NAME));
        setHeaderIfMissing(requestTemplate, HttpUtils.TOKEN_HEADER, HttpUtils.getToken(request));
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_APP_VERSION, request.getHeader(HttpUtils.HEADER_APP_VERSION));
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_UUID, request.getHeader(HttpUtils.HEADER_UUID));
    }
    
    /**
     * 确保用户身份相关的Header被设置到发出的请求中,
     * 如果Header不存在，则使用（已缓存的）HttpUtils方法来解析它们.
     *
     * @param requestTemplate 待发送请求的模板.
     * @param request         待发送请求的HTTP请求.
     */
    private void ensureUserHeaders(RequestTemplate requestTemplate, HttpServletRequest request) {
        // 如果Header已存在（例如内部服务调用），则直接传播
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_USER_ID, request.getHeader(HttpUtils.HEADER_USER_ID));
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_USERNAME, request.getHeader(HttpUtils.HEADER_USERNAME));
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_TENANT_ID, request.getHeader(HttpUtils.HEADER_TENANT_ID));
        setHeaderIfMissing(requestTemplate, HttpUtils.HEADER_NAME_USER, request.getHeader(HttpUtils.HEADER_NAME_USER));
        
        // 如果核心用户Header缺失，则解析用户信息并添加它们
        if (!requestTemplate.headers().containsKey(HttpUtils.HEADER_USER_ID)) {
            try {
                // HttpUtils中的方法在请求级别有缓存，所以这些调用是高效的
                Long userId = HttpUtils.getUserId();
                if (userId != null) {
                    requestTemplate.header(HttpUtils.HEADER_USER_ID, String.valueOf(userId));
                    requestTemplate.header(HttpUtils.HEADER_USERNAME, HttpUtils.getUsername());
                    requestTemplate.header(HttpUtils.HEADER_TENANT_ID, String.valueOf(HttpUtils.getTenantId()));
                    // 姓名在Header中传输需要进行URL编码
                    String name = HttpUtils.getNameInUser();
                    requestTemplate.header(HttpUtils.HEADER_NAME_USER, URLEncoder.encode(name, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                // 如果无法解析用户，则静默失败，以允许未经身份验证的调用
            }
        }
    }
    
    /**
     * 辅助方法：仅当Header不存在且值不为空时才添加.
     *
     * @param template 待添加Header的模板.
     * @param name     Header的名称.
     */
    private void setHeaderIfMissing(RequestTemplate template, String name, String value) {
        if (StrUtil.isNotBlank(value) && !template.headers().containsKey(name)) {
            template.header(name, value);
        }
    }
    
    /**
     * 根据Token从Redis获取用户身份信息.
     *
     * @param token      访问令牌
     * @param appVersion 应用版本，用于判断是否为移动端请求
     * @return UserPrincipleNoAuth 或 null（如果未找到）
     */
    public UserPrincipleNoAuth getUserUserPrinciple(String token, String appVersion) {
        if (Objects.isNull(tokenRedisTemplate)) {
            return null;
        }
        boolean isMobile = CharSequenceUtil.isNotBlank(appVersion);
        String redisKey = (isMobile ? TOKEN_APP_REDIS_KEY : TOKEN_WEB_REDIS_KEY) + token;
        String userInfo = (String) tokenRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isBlank(userInfo)) {
            return null;
        }
        return JacksonUtils.tryStr2Bean(userInfo, UserPrincipleNoAuth.class);
    }
}