package com.datafusion.manager.asset.constant;

/**
 * urn规则配置类.
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
public class UrnConstant {
    
    /**
     * 接口urn格式({组织名(goodwe)}:{业务域(vpp)}:{环境(k8s-pod)}:{服务类型(spring)}:{服务英文名称}:{method}:{url}).
     */
    public static final String API_FORMAT = "%s:%s:%s:%s:%s:%s:%s";
    
    /**
     * 接口urn格式({组织名(goodwe)}:{业务域(vpp)}:{环境(k8s-pod)}:{服务类型(spring)}:{服务英文名称}:{method}:{url}:{指标}).
     */
    public static final String API_METRIC_FORMAT = "%s:%s:%s:%s:%s:%s:%s:%s";
}
