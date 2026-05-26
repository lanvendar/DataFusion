package com.datafusion.manager.ingestion.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.datafusion.common.spring.typehandler.JsonNodeTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.UUID;

/**
 * 数据同步任务定义-主表实体.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ingestion_datasync_task", autoResultMap = true)
public class IngestionDatasyncTaskEntity extends BaseIdEntity {

    /**
     * 任务名称.
     */
    @TableField("name")
    private String name;

    /**
     * 任务编码，如JC2401010001.
     */
    @TableField("code")
    private String code;

    /**
     * 任务描述.
     */
    @TableField("description")
    private String description;

    /**
     * 来源数据源类型.
     */
    @TableField("source_ds_type")
    private String sourceDsType;

    /**
     * 来源数据源ID.
     */
    @TableField("source_ds_id")
    private UUID sourceDsId;

    /**
     * 来源实体ID.
     */
    @TableField("source_entity_id")
    private UUID sourceEntityId;

    /**
     * 来源数据源配置(JSON).
     */
    @TableField(value = "source_config", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode sourceConfig;

    /**
     * 目标数据源类型.
     */
    @TableField("target_ds_type")
    private String targetDsType;

    /**
     * 目标数据源ID.
     */
    @TableField("target_ds_id")
    private UUID targetDsId;

    /**
     * 目标实体ID.
     */
    @TableField("target_entity_id")
    private UUID targetEntityId;

    /**
     * 目标数据源配置(JSON).
     */
    @TableField(value = "target_config", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode targetConfig;

    /**
     * 发布状态.
     */
    @TableField("publish_status")
    private Boolean publishStatus;

    /**
     * 变量(JSON).
     */
    @TableField(value = "variables", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode variables;

    /**
     * 发布时间.
     */
    @TableField("publish_time")
    private Date publishTime;
}