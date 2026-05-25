package com.datafusion.manager.asset.dto;

import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import lombok.Data;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 菜单资源导入请求体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/2/12
 * @since 2026/2/12
 */
@Data
public class MenuResourceInfoDto {

    /**
     * 组织名称.
     */
    private String organization;

    /**
     * 业务域.
     */
    private String businessDomain;

    /**
     * 应用编码.
     */
    private String appCode;

    /**
     * 应用名称.
     */
    private String appName;

    /**
     * 菜单ID.
     */
    private Long menuId;

    /**
     * 菜单名称.
     */
    private String menuName;

    /**
     * 菜单类型.
     */
    private Byte componentType;

    /**
     * 关联的API资源ID列表.
     */
    private Map<UUID, Set<MetricsTagDto>> apiResourceIds;

}
