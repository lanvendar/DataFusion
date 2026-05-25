package com.datafusion.manager.development.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import com.datafusion.common.web.typehandler.JsonNodeTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.UUID;

/**
 * 数据开发-SQL脚本任务定义实体.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "develop_script_sql_task", autoResultMap = true)
public class DevelopScriptSqlTaskEntity extends BaseIdEntity {

    /**
     * 任务名称.
     */
    @TableField("name")
    private String name;

    /**
     * 任务编码，如KF2401010001.
     */
    @TableField("code")
    private String code;

    /**
     * 任务描述.
     */
    @TableField("description")
    private String description;

    /**
     * SQL脚本（库表 jsonb，可为 JSON 字符串或对象）.
     */
    @TableField(value = "script", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode script;

    /**
     * 变量(JSON).
     */
    @TableField(value = "variables", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode variables;

    /**
     * 发布状态，true表示已发布.
     */
    @TableField("publish_status")
    private Boolean publishStatus;

    /**
     * 删除标识：0正常 1已删除（软删）.
     */
    @TableField("model_status")
    private Integer modelStatus;

    /**
     * 发布时间.
     */
    @TableField("publish_time")
    private Date publishTime;

    /**
     * 任务分组ID.
     */
    @TableField("group_id")
    private UUID groupId;
}
