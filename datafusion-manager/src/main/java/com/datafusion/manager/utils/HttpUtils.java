package com.datafusion.manager.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.web.utils.ApplicationContextUtil;
import com.datafusion.manager.auth.SecurityInterceptor;
import com.datafusion.manager.auth.dto.UserPrincipleNoAuth;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Http工具类.
 *
 * @author : tingfei.xia
 * @version 1.0.0, 2025/9/8
 * @since : 2021/7/12 14:49
 */
public class HttpUtils {
    
    /**
     * HttpUtils.
     *
     * @throws IllegalAccessException IllegalAccessException
     */
    private HttpUtils() throws IllegalAccessException {
        throw new IllegalAccessException("static method");
    }
    
    /**
     * UNKNOWN.
     */
    private static final String UNKNOWN = "unknown";
    
    /**
     * HEADER_USER_ID.
     */
    public static final String HEADER_USER_ID = "userId";
    
    /**
     * HEADER_USERNAME.
     */
    public static final String HEADER_USERNAME = "username";
    
    /**
     * HEADER_NAME_USER.
     */
    public static final String HEADER_NAME_USER = "name";
    
    /**
     * HEADER_TENANT_ID.
     */
    public static final String HEADER_TENANT_ID = "tenantId";
    
    /**
     * HEADER_TENANT_NAME.
     */
    public static final String HEADER_TENANT_NAME = "tenantName";
    
    /**
     * TOKEN_HEADER.
     */
    public static final String TOKEN_HEADER = "access-token";
    
    /**
     * HEADER_UUID.
     */
    public static final String HEADER_UUID = "uuid";
    
    /**
     * HEADER_APP_VERSION.
     */
    public static final String HEADER_APP_VERSION = "appversion";
    
    /**
     * HEADER_SU_ID.
     */
    public static final String HEADER_SU_ID = "switchOperatorId";
    
    /**
     * HEADER_SU_USERNAME.
     */
    public static final String HEADER_SU_USERNAME = "switchOperator";
    
    /**
     * HEADER_SU_NAME.
     */
    public static final String HEADER_SU_NAME = "switchOperatorName";
    
    /**
     * DEFAULT_USER_NAME.
     */
    public static final String DEFAULT_USER_NAME = "system";
    
    /**
     * HEADER_LANG.
     */
    public static final String HEADER_LANG = "lang";
    
    /**
     * HEADER_PAGE_KEY.
     */
    public static final String HEADER_PAGE_KEY = "Page-Key";
    
    /**
     * HEADER_PROJECT_NAME.
     */
    public static final String HEADER_PROJECT_NAME = "Project-Name";
    
    /**
     * APP_APPLICATION_CODE.
     */
    public static final String APP_APPLICATION_CODE = "App-Application-Code";
    
    /**
     * 获取当前用户ID,
     * 优先检查Header，然后回退到从Token解析的用户信息.
     *
     * @return 当前用户ID
     */
    public static Long getUserId() {
        return getInfoFromRequest(HEADER_USER_ID, Long::valueOf, UserPrincipleNoAuth::getId);
    }
    
    /**
     * 获取当前用户名.
     *
     * @return 当前用户名
     */
    public static String getUsername() {
        return getInfoFromRequest(HEADER_USERNAME, Function.identity(), UserPrincipleNoAuth::getUsername);
    }
    
    /**
     * 获取当前用户名，失败时返回默认值.
     *
     * @return 当前用户名，失败时返回默认值
     */
    public static String getUsernameNoException() {
        try {
            return getUsername();
        } catch (Exception e) {
            return DEFAULT_USER_NAME;
        }
    }
    
    /**
     * 获取当前用户的真实姓名.
     *
     * @return 当前用户真实姓名
     */
    public static String getNameInUser() {
        String name = getInfoFromRequest(HEADER_NAME_USER, Function.identity(), UserPrincipleNoAuth::getName);
        return URLDecoder.decode(name, StandardCharsets.UTF_8);
    }
    
    /**
     * 获取当前用户的姓名，失败时返回默认值.
     *
     * @return 当前用户姓名，失败时返回默认值
     */
    public static String getCurrentUserName() {
        try {
            return getNameInUser();
        } catch (Exception e) {
            return DEFAULT_USER_NAME;
        }
    }
    
    /**
     * 获取当前用户的租户ID.
     *
     * @return 当前用户的租户ID
     */
    public static Long getTenantId() {
        return getInfoFromRequest(HEADER_TENANT_ID, Long::valueOf, UserPrincipleNoAuth::getTenantId);
    }
    
    /**
     * 获取当前用户的租户名称.
     *
     * @return 当前用户的租户名称
     */
    public static String getTenantName() {
        String name = getInfoFromRequest(HEADER_TENANT_NAME, Function.identity(), UserPrincipleNoAuth::getTenantName);
        return URLDecoder.decode(name, StandardCharsets.UTF_8);
    }
    
    /**
     * 获取切换操作者的ID.
     *
     * @return 切换操作者的ID
     */
    public static Long getSwitchOperatorId() {
        return getInfoFromRequest(HEADER_SU_ID, Long::valueOf, UserPrincipleNoAuth::getSwitchOperatorId);
    }
    
    /**
     * 获取切换操作者的用户名.
     *
     * @return 切换操作者的用户名
     */
    public static String getSwitchOperator() {
        return getInfoFromRequest(HEADER_SU_USERNAME, Function.identity(), UserPrincipleNoAuth::getSwitchOperator);
    }
    
    /**
     * 获取切换操作者的姓名.
     *
     * @return 获取切换操作者的姓名
     */
    public static String getSwitchOperatorName() {
        String name = getInfoFromRequest(HEADER_SU_NAME, Function.identity(), UserPrincipleNoAuth::getSwitchOperatorName);
        return URLDecoder.decode(name, StandardCharsets.UTF_8);
    }
    
    /**
     * 通用辅助方法：通过先检查请求Header，然后回退到UserPrinciple对象来检索信息.
     *
     * @param headerName      HTTP Header的名称
     * @param headerConverter 将Header字符串转换为目标类型的函数
     * @param principleMapper 从UserPrinciple对象中提取值的函数
     * @return 请求的信息，如果找不到则抛出异常
     */
    private static <T> T getInfoFromRequest(String headerName, Function<String, T> headerConverter,
                                            Function<UserPrincipleNoAuth, T> principleMapper) {
        HttpServletRequest request = getHttpServletRequest();
        String headerValue = request.getHeader(headerName);
        if (CharSequenceUtil.isNotBlank(headerValue)) {
            return headerConverter.apply(headerValue);
        }
        
        return Optional.ofNullable(getUserPrinciple(request))
                .map(principleMapper)
                .orElseThrow(() -> new CommonException(ErrorCodeEnum.USER_ERROR_A0220, "获取用户信息失败: " + headerName));
    }
    
    /**
     * 获取当前请求的完整用户身份信息对象,
     * 结果会被缓存在请求属性中，以避免对Redis的多次查找.
     *
     * @return 当前请求的完整用户身份信息对象
     */
    public static UserPrincipleNoAuth getUserPrinciple() {
        return getUserPrinciple(getHttpServletRequest());
    }
    
    /**
     * 为给定请求获取用户身份信息，使用请求作用域缓存.
     *
     * @param request 请求对象
     * @return 用户身份信息对象
     */
    private static UserPrincipleNoAuth getUserPrinciple(HttpServletRequest request) {
        // 请求中未命中，从Redis加载
        String token = getToken(request);
        String appVersion = request.getHeader(HEADER_APP_VERSION);
        SecurityInterceptor securityInterceptor = ApplicationContextUtil.getBean(SecurityInterceptor.class);
        return securityInterceptor.getUserUserPrinciple(token, appVersion);
    }
    
    /**
     * 从当前请求上下文中获取访问令牌.
     *
     * @return 访问令牌
     */
    public static String getToken() {
        return getToken(getHttpServletRequest());
    }
    
    /**
     * 从请求中获取访问令牌，优先检查Cookie，然后检查Header.
     *
     * @param request 请求对象
     * @return 访问令牌
     */
    public static String getToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (Objects.nonNull(cookies)) {
            for (Cookie cookie : cookies) {
                if (Objects.equals(cookie.getName(), TOKEN_HEADER) && CharSequenceUtil.isNotBlank(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        String token = request.getHeader(TOKEN_HEADER);
        if (StrUtil.isBlank(token)) {
            throw new CommonException(ErrorCodeEnum.USER_ERROR_A0220, "在Cookie或Header中未找到认证令牌");
        }
        return token;
    }
    
    /**
     * 获取当前请求的HttpServletRequest对象.
     *
     * @return 当前请求的HttpServletRequest对象
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(attributes)) {
            throw new CommonException(ErrorCodeEnum.SYSTEM_ERROR_B0001, "请求上下文不可用");
        }
        return attributes.getRequest();
    }
    
    /**
     * 获取当前请求的HttpServletRequest对象.
     *
     * @param header 请求头名称
     * @return 当前请求的HttpServletRequest对象
     */
    public static String getHeaderFromHttp(String header) {
        return getHttpServletRequest().getHeader(header);
    }
    
    /**
     * 获取当前请求的语言.
     *
     * @return 当前请求的语言
     */
    public static String getLang() {
        return getLang(getHttpServletRequest());
    }
    
    /**
     * 获取当前请求的语言.
     *
     * @param request 请求对象
     * @return 当前请求的语言
     */
    public static String getLang(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (Objects.nonNull(cookies)) {
            for (Cookie cookie : cookies) {
                if (Objects.equals(cookie.getName(), HEADER_LANG) && CharSequenceUtil.isNotBlank(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    
    /**
     * 获取当前请求的IP.
     *
     * @param request 请求对象
     * @return 当前请求的IP
     */
    public static String getRequestIp(HttpServletRequest request) {
        String[] headers = {"x-forwarded-for", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim(); // 处理多级代理的情况
            }
        }
        return request.getRemoteAddr();
    }
    
    /**
     * 判断当前请求是否为移动端请求.
     *
     * @return true表示是移动端请求，false表示不是移动端请求
     */
    public static boolean isMobile() {
        return CharSequenceUtil.isNotBlank(getHeaderFromHttp(HEADER_APP_VERSION));
    }
    
    /**
     * 获取当前请求的项目名称.
     *
     * @return 当前请求的项目名称
     */
    public static String getProjectName() {
        return Optional.ofNullable(getProjectNameOrNull())
                .orElseThrow(() -> new CommonException(ErrorCodeEnum.SYSTEM_ERROR_B0001, "从Header获取项目名称失败"));
    }
    
    /**
     * 获取当前请求的项目名称.
     *
     * @return 当前请求的项目名称
     */
    public static String getProjectNameOrNull() {
        return getHeaderFromHttp(HEADER_PROJECT_NAME);
    }
    
    /**
     * 获取当前请求的App应用代码.
     *
     * @return 当前请求的App应用代码
     */
    public static String getAppApplicationCode() {
        return Optional.ofNullable(getAppApplicationCodeOrNull())
                .orElseThrow(() -> new CommonException(ErrorCodeEnum.SYSTEM_ERROR_B0001, "从Header获取App应用代码失败"));
    }
    
    /**
     * 获取当前请求的App应用代码.
     *
     * @return 当前请求的App应用代码
     */
    public static String getAppApplicationCodeOrNull() {
        return getHeaderFromHttp(APP_APPLICATION_CODE);
    }
}
