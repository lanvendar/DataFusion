package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-流程信息实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_flow_info")
public class FlowInfoEntity extends BaseIdEntity {

    /**
     * 流程名称.
     */
    @TableField("flow_name")
    private String flowName;

    /**
     * 流程编码.
     */
    @TableField("flow_code")
    private String flowCode;

    /**
     * 流程分组.
     */
    @TableField("group_id")
    private UUID groupId;

    /**
     * 流程描述.
     */
    @TableField("description")
    private String description;

    /**
     * 流程类型.
     */
    @TableField("flow_type")
    private String flowType;

    /**
     * 流程参数信息(JSON).
     */
    @TableField("flow_param")
    private JsonNode flowParam;

    /**
     * 调度开始时间.
     */
    @TableField("start_time")
    private Long startTime;

    /**
     * 调度结束时间.
     */
    @TableField("end_time")
    private Long endTime;

    /**
     * 是否调度: false-未调度, true-调度中.
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 依赖事件ID, 逗号分割.
     */
    @TableField("dep_event_ids")
    private String depEventIds;

    /**
     * 事件ID.
     */
    @TableField("event_id")
    private UUID eventId;

    /**
     * 发布状态: false-未发布, true-已发布.
     */
    @TableField("publish_state")
    private Boolean publishState;

    /**
     * 发布版本(格式:时间戳, 等价与发布时间).
     */
    @TableField("publish_version")
    private Long publishVersion;

    /**
     * 触发器ID.
     */
    @TableField("trigger_id")
    private UUID triggerId;

    /**
     * 流程前端视图信息(JSON).
     */
    @TableField("view")
    private JsonNode view;
}
