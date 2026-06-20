package com.datafusion.manager.system.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.datafusion.common.spring.typehandler.JsonNodeTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

/**
 * 系统-插件配置实体.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Data
@TableName(value = "system_plugin_config", autoResultMap = true)
public class PluginConfigEntity extends BaseIdEntity {

    /**
     * 插件名称.
     */
    @TableField("plugin_name")
    private String pluginName;

    /**
     * 插件类型.
     */
    @TableField("plugin_type")
    private String pluginType;

    /**
     * 运行模式.
     */
    @TableField("run_mode")
    private String runMode;

    /**
     * 描述.
     */
    @TableField("description")
    private String description;

    /**
     * 插件配置.
     */
    @TableField(value = "plugin_param", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode pluginParam;

    /**
     * 是否模板数据.
     */
    @TableField("is_template")
    private Boolean isTemplate;

    /**
     * 删除状态: 0-正常, 1-删除.
     */
    @TableField("is_del")
    private Short isDel;

    /**
     * 租户ID.
     */
    @TableField("tenant_id")
    private UUID tenantId;
}
