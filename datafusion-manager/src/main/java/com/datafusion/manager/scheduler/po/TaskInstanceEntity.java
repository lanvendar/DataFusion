package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-任务实例实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_task_instance")
public class TaskInstanceEntity extends BaseIdEntity {

    /**
     * 流程ID.
     */
    @TableField("flow_id")
    private UUID flowId;

    /**
     * 流程实例ID.
     */
    @TableField("flow_instance_id")
    private UUID flowInstanceId;

    /**
     * 任务ID.
     */
    @TableField("task_id")
    private UUID taskId;

    /**
     * 任务类型.
     */
    @TableField("task_type")
    private String taskType;

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
     * 任务变量参数(JSON).
     */
    @TableField("task_param")
    private JsonNode taskParam;

    /**
     * 渲染后的任务定义(JSON).
     */
    @TableField("task_data")
    private JsonNode taskData;

    /**
     * 组件数据(JSON).
     */
    @TableField("plugin_data")
    private JsonNode pluginData;

    /**
     * 任务视图(JSON).
     */
    @TableField("view")
    private JsonNode view;

    /**
     * 依赖事件ID.
     */
    @TableField("dep_event_ids")
    private String depEventIds;

    /**
     * 产生事件ID.
     */
    @TableField("event_id")
    private UUID eventId;

    /**
     * 任务实例状态.
     */
    @TableField("status")
    private String status;

    /**
     * 任务实例开始时间.
     */
    @TableField("start_time")
    private Long startTime;

    /**
     * 任务实例结束时间.
     */
    @TableField("end_time")
    private Long endTime;

    /**
     * 耗时.
     */
    @TableField("cost_time")
    private Integer costTime;

    /**
     * 上一个任务实例ID.
     */
    @TableField("last_instance_id")
    private String lastInstanceId;

    /**
     * 下一个任务实例ID.
     */
    @TableField("next_instance_id")
    private String nextInstanceId;

    /**
     * 执行节点ID.
     */
    @TableField("worker_id")
    private UUID workerId;

    /**
     * 返回值(JSON).
     */
    @TableField("worker_result")
    private JsonNode workerResult;
}
