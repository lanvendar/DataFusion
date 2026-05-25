package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-事件信息实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_event_info")
public class EventInfoEntity extends BaseIdEntity {

    /**
     * 事件名称.
     */
    @TableField("event_name")
    private String eventName;

    /**
     * 事件类型.
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 流程ID.
     */
    @TableField("flow_id")
    private UUID flowId;

    /**
     * 任务ID.
     */
    @TableField("task_id")
    private UUID taskId;
}
