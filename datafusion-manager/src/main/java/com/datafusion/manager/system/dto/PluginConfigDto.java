package com.datafusion.manager.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 系统-插件配置响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Data
@Schema(name = "PluginConfigDto", description = "插件配置响应Dto")
public class PluginConfigDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 插件名称.
     */
    @Schema(name = "pluginName", description = "插件名称")
    private String pluginName;

    /**
     * 插件类型.
     */
    @Schema(name = "pluginType", description = "插件类型")
    private String pluginType;

    /**
     * 运行模式.
     */
    @Schema(name = "runMode", description = "运行模式")
    private String runMode;

    /**
     * 描述.
     */
    @Schema(name = "description", description = "描述")
    private String description;

    /**
     * 插件配置.
     */
    @Schema(name = "pluginParam", description = "插件配置")
    private JsonNode pluginParam;

    /**
     * 是否模板数据.
     */
    @Schema(name = "isTemplate", description = "是否模板数据")
    private Boolean isTemplate;

    /**
     * 删除状态.
     */
    @Schema(name = "isDel", description = "删除状态")
    private Short isDel;

    /**
     * 租户ID.
     */
    @Schema(name = "tenantId", description = "租户ID")
    private UUID tenantId;

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String creator;

    /**
     * 修改人.
     */
    @Schema(name = "updater", description = "修改人")
    private String updater;

    /**
     * 创建时间.
     */
    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    /**
     * 修改时间.
     */
    @Schema(name = "updateTime", description = "修改时间")
    private Date updateTime;
}
