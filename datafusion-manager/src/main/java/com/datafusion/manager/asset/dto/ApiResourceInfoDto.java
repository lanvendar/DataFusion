package com.datafusion.manager.asset.dto;

import lombok.Data;

/**
 * api资源导入实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class ApiResourceInfoDto {

    /**
     * 组织名称.
     */
    private String organization;

    /**
     * 业务域.
     */
    private String businessDomain;

    /**
     * 环境.
     */
    private String env;

    /**
     * 服务类型.
     */
    private String serviceType;

    /**
     * 服务英文名称.
     */
    private String serviceEnName;

    /**
     * 服务中文名称.
     */
    private String serviceCnName;

    /**
     * 请求方式.
     */
    private String requestType;

    /**
     * 接口地址url.
     */
    private String requestUrl;

    /**
     * 接口名称.
     */
    private String requestUrlName;

    /**
     * 接口basePath.
     */
    private String basePath;

}
