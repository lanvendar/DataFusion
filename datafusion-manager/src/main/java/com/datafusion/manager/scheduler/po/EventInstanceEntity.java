package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * 调度-事件实例实体.
 *
 * <p>注意: 该表无 creator/create_time 等审计字段，不继承 BaseEntity.</p>
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_event_instance")
public class EventInstanceEntity implements Serializable {

    /**
     * 主键.
     */
    @TableId("id")
    @TableField("id")
    private UUID id;

    /**
     * 事件ID.
     */
    @TableField("event_id")
    private UUID eventId;

    /**
     * 事件名称.
     */
    @TableField("event_name")
    private String eventName;

    /**
     * 事件类型: TASK, FLOW.
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 流程实例ID.
     */
    @TableField("flow_instance_id")
    private UUID flowInstanceId;

    /**
     * 任务实例ID.
     */
    @TableField("task_instance_id")
    private UUID taskInstanceId;

    /**
     * 事件生效时间.
     */
    @TableField("effect_time")
    private Long effectTime;

    /**
     * 事件开始生效时间.
     */
    @TableField("effect_begin_time")
    private Long effectBeginTime;

    /**
     * 事件结束生效时间.
     */
    @TableField("effect_end_time")
    private Long effectEndTime;
}
