package com.datafusion.manager.ingestion.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.web.po.BaseIdEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 数据同步任务定义-从表实体(字段映射).
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ingestion_datasync_field", autoResultMap = true)
public class IngestionDatasyncFieldEntity extends BaseIdEntity{

    /**
     * 任务定义ID.
     */
    @TableField("task_id")
    private UUID taskId;

    /**
     * 数据来源类型(SOURCE/TARGET).
     */
    @TableField("source_target")
    private String sourceTarget;

    /**
     * 列名.
     */
    @TableField("column_name")
    private String columnName;

    /**
     * 数据类型.
     */
    @TableField("data_type")
    private String dataType;

    /**
     * 序号.
     */
    @TableField("column_index")
    private Integer columnIndex;

}