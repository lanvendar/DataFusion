package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import lombok.Data;

/**
 * 调度-触发器信息实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_trigger_info")
public class TriggerInfoEntity extends BaseIdEntity {

    /**
     * 触发器名称.
     */
    @TableField("name")
    private String name;

    /**
     * 调度策略.
     */
    @TableField("policy")
    private String policy;

    /**
     * 触发器类型: 0-CRON, 1-INTERVAL.
     */
    @TableField("type")
    private String type;

    /**
     * cron表达式.
     */
    @TableField("cron")
    private String cron;

    /**
     * 周期间隔时间, 单位/分钟.
     */
    @TableField("interval")
    private Integer interval;
}
