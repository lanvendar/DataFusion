package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * 调度-流程任务编排关系实体(DAG边).
 *
 * <p>注意: 该表无 creator/create_time 等审计字段, 不继承 BaseIdEntity.</p>
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_task_link")
public class TaskLinkEntity implements Serializable {

    /**
     * 连接ID.
     */
    @TableId("id")
    @TableField("id")
    private UUID id;

    /**
     * 流程ID.
     */
    @TableField("flow_id")
    private UUID flowId;

    /**
     * 开始节点ID(上游任务).
     */
    @TableField("start_id")
    private UUID startId;

    /**
     * 结束节点ID(下游任务).
     */
    @TableField("end_id")
    private UUID endId;

    /**
     * 连线视图(JSON).
     */
    @TableField("view")
    private JsonNode view;
}