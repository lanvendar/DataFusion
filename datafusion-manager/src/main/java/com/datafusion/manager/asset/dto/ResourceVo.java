package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/19
 * @since 2025/11/19
 */
@Data
public class ResourceVo {

    /**
     * id.
     */
    @Schema(name = "id;", description = "id")
    private UUID id;

    /**
     * 资源名称.
     */
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;

    /**
     * 资源名称.
     */
    @Schema(name = "resourceTag", description = "资源标签")
    private Integer resourceTag;

    /**
     * 资源标签名称.
     */
    @Schema(name = "resourceTagDesc", description = "资源标签名称")
    private String resourceTagDesc;

    /**
     * 资源类型.
     */
    @Schema(name = "resourceType", description = "资源类型")
    private String resourceType;

    /**
     * 资源类型.
     */
    @Schema(name = "resourceTypeDesc", description = "资源类型描述")
    private String resourceTypeDesc;

    /**
     * status.
     */
    @Schema(name = "status", description = "状态")
    private Integer status;

    /**
     * resourceSnapshot.
     */
    @Schema(name = "resourceSnapshot", description = "资源快照")
    private String resourceSnapshot;

    /**
     * resultSnapshot.
     */
    @Schema(name = "resultSnapshot", description = "血缘快照")
    private String resultSnapshot;

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String creator;

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String createTime;

    /**
     * 更新人.
     */
    @Schema(name = "updater", description = "更新人")
    private String updater;

    /**
     * 更新时间.
     */
    @Schema(name = "updateTime", description = "更新时间")
    private String updateTime;

    /**
     * 结果.
     */
    @Schema(name = "result", description = "结果")
    private String result;

}
