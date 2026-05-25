package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-流程实例实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_flow_instance")
public class FlowInstanceEntity extends BaseIdEntity {

    /**
     * 流程ID.
     */
    @TableField("flow_id")
    private UUID flowId;

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
     * 流程类型.
     */
    @TableField("flow_type")
    private String flowType;

    /**
     * 流程实例状态.
     */
    @TableField("status")
    private String status;

    /**
     * 触发器ID.
     */
    @TableField("trigger_id")
    private String triggerId;

    /**
     * 发布版本.
     */
    @TableField("publish_version")
    private Long publishVersion;

    /**
     * 流程参数(JSON).
     */
    @TableField("flow_param")
    private JsonNode flowParam;

    /**
     * 全局依赖事件ID, 逗号分割.
     */
    @TableField("dep_event_ids")
    private String depEventIds;

    /**
     * 事件ID.
     */
    @TableField("event_id")
    private UUID eventId;

    /**
     * 调度时间.
     */
    @TableField("schedule_time")
    private Long scheduleTime;

    /**
     * 开始时间.
     */
    @TableField("start_time")
    private Long startTime;

    /**
     * 结束时间.
     */
    @TableField("end_time")
    private Long endTime;

    /**
     * 流程DAG快照(JSON).
     */
    @TableField("flow_dag_snapshot")
    private JsonNode flowDagSnapshot;
}
