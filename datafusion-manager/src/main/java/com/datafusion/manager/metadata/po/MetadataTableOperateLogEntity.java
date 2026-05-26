package com.datafusion.manager.metadata.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import com.datafusion.common.spring.typehandler.JsonNodeTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 表结构同步记录表.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/15
 * @since 2025/9/15
 */
@Data
@Accessors(chain = true)
@TableName("metadata_table_operate_log")
public class MetadataTableOperateLogEntity extends BaseIdEntity {
    
    /**
     * 0:批量创建|1:批量对比.
     */
    @TableField("operate_type")
    private int operateType;
    
    /**
     * 源数据库.
     */
    @TableField("source_datasource_id")
    private UUID sourceDatasourceId;
    
    /**
     * 目标数据库.
     */
    @TableField("target_datasource_id")
    private UUID targetDatasourceId;
    
    /**
     * 操作日期.
     */
    @TableField("operate_time")
    private LocalDateTime operateTime;
    
    /**
     * 范围快照.
     */
    @TableField(value = "snapshot_step_1", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode snapshotStep1;
    
    /**
     * 对比快照.
     */
    @TableField(value = "snapshot_step_2", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode snapshotStep2;
    
    /**
     * 执行快照.
     */
    @TableField(value = "snapshot_step_3", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode snapshotStep3;

}
