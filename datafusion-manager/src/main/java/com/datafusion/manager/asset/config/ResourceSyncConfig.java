package com.datafusion.manager.asset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步配置.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/21
 * @since 2026/01/21
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "datafusion.resource.sync")
public class ResourceSyncConfig {

    /**
     * 组织名称.
     */
    private String organization = "goodwe";

    /**
     * 业务域.
     */
    private String businessDomain = "vpp";

    /**
     * 环境.
     */
    private String env = "k8s-pod";

    /**
     * 服务类型.
     */
    private String serviceType = "spring";

    /**
     * 服务英文名称.
     */
    private String serviceEnName = "middle-openapi-gw";

    /**
     * 服务英文名称.
     */
    private String serviceEnNameUN = "secp-biz-data";

    /**
     * 请求类型.
     */
    private String requestType = "POST";

    /**
     * 数据库类型.
     */
    private String databaseType = "hologres";

    /**
     * 数据源名称.
     */
    private String datasourceName = "平台hologres生产secpdw库dw";

    /**
     * 数据库名称.
     */
    private String databaseName = "secpdw";

    /**
     * Schema名称.
     */
    private String schemaName = "dw";

    /**
     * vpp与服务对应关系.
     * key : vpp
     * values : 服务英文名称
     */
    private Map<String, List<String>> vppServiceMap = new HashMap<>();

    /**
     * 通过服务英文名称获取vpp.
     *
     * @param serviceEnName 服务英文名称
     * @return vpp
     */
    public String getVppByService(String serviceEnName) {
        if (vppServiceMap == null || vppServiceMap.isEmpty()) {
            return businessDomain;
        }

        for (Map.Entry<String, List<String>> entry : vppServiceMap.entrySet()) {
            if (entry.getValue().contains(serviceEnName)) {
                return entry.getKey();
            }
        }
        return businessDomain;
    }

    /**
     * 解析链路时，需要将如下配置中的占位符路径完整解析为node.
     * key： 服务英文名称，如 middle-openapi-gw
     * values: 需要解析的api，如 custom/{apiPath} ，解析为 /custom/device-day-data-rt
     */
    private Map<String, List<String>> transRealPathMap = new HashMap<>();

    /**
     * 解析链路时需要.
     * key： 服务英文名称，如 middle-openapi-gw
     * values: bashPath
     */
    private Map<String, String> basePathMap = new HashMap<>();

    /**
     * 合并后的映射表：无论是 T0 还是 T1 的值，都指向时间维度.
     */
    private Map<String, String> timeDimensionMap = new HashMap<>();

}
