package com.datafusion.manager.scheduler.model;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotBlank;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 业务来源定位信息.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/13
 * @since 1.0.0
 */
@Data
@Schema(name = "BusinessSourceRoute", description = "业务来源定位信息")
public class BusinessSourceRoute {

    /**
     * 定位协议前缀.
     */
    private static final String PROTOCOL_PREFIX = "bizref:v1";

    /**
     * 来源业务系统.
     */
    @NotBlank(message = "来源业务系统不能为空")
    @Schema(name = "bizSystem", description = "来源业务系统")
    private String bizSystem;

    /**
     * 来源业务主键.
     */
    @NotBlank(message = "来源业务主键不能为空")
    @Schema(name = "bizKey", description = "来源业务主键")
    private String bizKey;

    /**
     * 业务定义版本.
     */
    @NotBlank(message = "业务定义版本不能为空")
    @Schema(name = "bizVersion", description = "业务定义版本或更新时间")
    private String bizVersion;

    /**
     * 业务页面地址.
     */
    @Schema(name = "bizUrl", description = "业务页面地址")
    private String bizUrl;

    /**
     * 转换为持久化定位串.
     *
     * @return 持久化定位串
     */
    public String toSourceRoute() {
        String route = identity() + ":bizVersion=" + encode(bizVersion);
        return StringUtils.isBlank(bizUrl) ? route : route + ":bizUrl=" + encode(bizUrl);
    }

    /**
     * 返回稳定业务身份串.
     *
     * @return 业务身份串
     */
    public String identity() {
        validateRequiredFields();
        return PROTOCOL_PREFIX + ":bizSystem=" + encode(bizSystem) + ":bizKey=" + encode(bizKey);
    }

    /**
     * 返回编码后的业务系统.
     *
     * @return 编码值
     */
    public String encodedBizSystem() {
        validateRequiredFields();
        return encode(bizSystem);
    }

    /**
     * 返回编码后的业务主键.
     *
     * @return 编码值
     */
    public String encodedBizKey() {
        validateRequiredFields();
        return encode(bizKey);
    }

    /**
     * 解析持久化定位串.
     *
     * @param sourceRoute 持久化定位串
     * @return 业务来源定位信息
     */
    public static BusinessSourceRoute parse(String sourceRoute) {
        if (StringUtils.isBlank(sourceRoute)) {
            return null;
        }
        String[] segments = sourceRoute.split(":", -1);
        boolean valid = segments.length >= 5 && segments.length <= 6
                && "bizref".equals(segments[0]) && "v1".equals(segments[1])
                && segments[2].startsWith("bizSystem=") && segments[3].startsWith("bizKey=")
                && segments[4].startsWith("bizVersion=")
                && (segments.length == 5 || segments[5].startsWith("bizUrl="));
        if (!valid) {
            throw invalidRoute();
        }
        try {
            BusinessSourceRoute route = new BusinessSourceRoute();
            route.setBizSystem(decode(segments[2].substring("bizSystem=".length())));
            route.setBizKey(decode(segments[3].substring("bizKey=".length())));
            route.setBizVersion(decode(segments[4].substring("bizVersion=".length())));
            if (segments.length == 6) {
                route.setBizUrl(decode(segments[5].substring("bizUrl=".length())));
            }
            route.validateRequiredFields();
            return route;
        } catch (IllegalArgumentException e) {
            throw invalidRoute();
        }
    }

    private void validateRequiredFields() {
        if (StringUtils.isAnyBlank(bizSystem, bizKey, bizVersion)) {
            throw invalidRoute();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static CommonException invalidRoute() {
        return new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "业务来源定位信息格式非法");
    }
}
