package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-任务��息实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_task_info")
public class TaskInfoEntity extends BaseIdEntity {

    /**
     * 任务名称.
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 任务编码.
     */
    @TableField("task_code")
    private String taskCode;

    /**
     * 任务描述.
     */
    @TableField("description")
    private String description;

    /**
     * 任务类型ID.
     */
    @TableField("task_type_id")
    private String taskTypeId;

    /**
     * 任务类型.
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 任务变量参数(JSON).
     */
    @TableField("task_param")
    private JsonNode taskParam;

    /**
     * 任务定义(JSON).
     */
    @TableField("definition")
    private JsonNode definition;

    /**
     * 是否绑定流程.
     */
    @TableField("is_bound")
    private Boolean isBound;

    /**
     * 流程ID.
     */
    @TableField("flow_id")
    private UUID flowId;

    /**
     * 执行组件ID.
     */
    @TableField("plugin_id")
    private UUID pluginId;

    /**
     * 任务前端视图(JSON).
     */
    @TableField("view")
    private JsonNode view;

    /**
     * 依赖事件ID.
     */
    @TableField("dep_event_ids")
    private String depEventIds;

    /**
     * 事件ID.
     */
    @TableField("event_id")
    private UUID eventId;

    /**
     * 是否启用.
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 任务同步标识: 修改业务任务时更新false, 提交时置为true.
     */
    @TableField("sync_flag")
    private Boolean syncFlag;

    /**
     * 原始业务定位路由.
     */
    @TableField("source_route")
    private String sourceRoute;
}
