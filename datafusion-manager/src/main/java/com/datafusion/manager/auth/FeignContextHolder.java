package com.datafusion.manager.auth;

import cn.hutool.core.text.CharSequenceUtil;
import com.datafusion.manager.auth.constant.AssertMsgPool;
import com.datafusion.manager.auth.dto.FeignAuthentication;
import com.datafusion.manager.utils.HttpUtils;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * FeignContextHolder.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/8
 * @since 2025/9/8
 */
public class FeignContextHolder {
    
    /**
     * FeignContextHolder.
     * @throws IllegalAccessException IllegalAccessException
     */
    private FeignContextHolder() throws IllegalAccessException {
        throw new IllegalAccessException();
    }
    
    /**
     * 保存线程的认证信息.
     */
    @SuppressWarnings("checkstyle:ConstantName")
    private static final ThreadLocal<FeignAuthentication> authenticationHolder = new ThreadLocal<>();
    
    /**
     * setFeignAuthentication.
     * @param feignAuthentication feignAuthentication
     */
    public static void setFeignAuthentication(FeignAuthentication feignAuthentication) {
        Assert.notNull(feignAuthentication, AssertMsgPool.PARAM_NOT_NULL);
        if (CharSequenceUtil.isBlank(feignAuthentication.getToken())) {
            throw new IllegalArgumentException(AssertMsgPool.PARAM_NOT_NULL);
        }
        authenticationHolder.set(feignAuthentication);
    }
    
    /**
     * getFeignAuthenticationFromApplicationContext.
     * @return FeignAuthentication
     */
    public static FeignAuthentication getFeignAuthenticationFromApplicationContext() {
        FeignAuthentication feignAuthentication = new FeignAuthentication();
        String token = HttpUtils.getToken();
        feignAuthentication.setToken(token);
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            HttpServletRequest request = requestAttributes.getRequest();
            String uuid = request.getHeader(HttpUtils.HEADER_UUID);
            if (CharSequenceUtil.isNotBlank(uuid)) {
                feignAuthentication.setUuid(uuid);
            }
            String appVersion = request.getHeader(HttpUtils.HEADER_APP_VERSION);
            if (CharSequenceUtil.isNotBlank(appVersion)) {
                feignAuthentication.setAppVersion(appVersion);
            }
            String pageKey = request.getHeader(HttpUtils.HEADER_PAGE_KEY);
            if (CharSequenceUtil.isNotBlank(pageKey)) {
                // Bug修复：原代码错误地将appVersion赋值给了pageKey
                feignAuthentication.setPageKey(pageKey);
            }
            String projectName = request.getHeader(HttpUtils.HEADER_PROJECT_NAME);
            if (CharSequenceUtil.isNotBlank(projectName)) {
                // Bug修复：原代码错误地将appVersion赋值给了projectName
                feignAuthentication.setProjectName(projectName);
            }
        }
        
        return feignAuthentication;
    }
    
    /**
     * getFeignAuthentication.
     * @return FeignAuthentication
     */
    public static FeignAuthentication getFeignAuthentication() {
        return authenticationHolder.get();
    }
    
    /**
     * cleanFeignAuthentication.
     */
    public static void cleanFeignAuthentication() {
        authenticationHolder.remove();
    }
}