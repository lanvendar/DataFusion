package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import lombok.Data;

/**
 * 调度-变量信息实体.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Data
@TableName("scheduler_variable_info")
public class VariableInfoEntity extends BaseIdEntity {

    /**
     * 变量编码.
     */
    @TableField("code")
    private String code;

    /**
     * 变量名称.
     */
    @TableField("name")
    private String name;

    /**
     * 变量类型: CUSTOM(自定义), SYSTEM(系统全局).
     */
    @TableField("type")
    private String type;

    /**
     * 变量值类型.
     */
    @TableField("value_type")
    private String valueType;

    /**
     * 值.
     */
    @TableField("value")
    private String value;
}
