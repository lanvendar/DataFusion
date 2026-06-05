package com.datafusion.manager.system.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import lombok.Data;

import java.util.UUID;

/**
 * 系统-任务类型配置实体.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@TableName("system_task_type_config")
public class TaskTypeConfigEntity extends BaseIdEntity {

    /**
     * 任务类型.
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 默认插件ID.
     */
    @TableField("default_plugin_id")
    private UUID defaultPluginId;

    /**
     * 插件类型.
     */
    @TableField(value = "plugin_type", updateStrategy = FieldStrategy.ALWAYS)
    private String pluginType;

    /**
     * 租户ID.
     */
    @TableField("tenant_id")
    private UUID tenantId;
}
