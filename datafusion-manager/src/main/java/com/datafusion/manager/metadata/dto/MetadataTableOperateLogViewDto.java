package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对比操作日志详情.
 * @author xufeng
 * @version 1.0.0, 2025/9/18
 * @since 2025/9/18
 */
@Data
@Schema(name = "MetadataTableOperateLogViewDto", description = "对比操作日志详情.")
public class MetadataTableOperateLogViewDto {
    
    /**
     * 主键id.
     */
    @Schema(name = "id", description = "主键id")
    private UUID id;
    
    /**
     * 0:批量创建|1:批量对比.
     */
    @Schema(name = "operateType", description = "0:批量创建|1:批量对比.")
    private int operateType;
    
    /**
     * 源数据库id.
     */
    @Schema(name = "sourceDatasourceId", description = "源数据库id")
    private UUID sourceDatasourceId;
    
    /**
     * 目标数据库id.
     */
    @Schema(name = "targetDatasourceId", description = "目标数据库id")
    private UUID targetDatasourceId;
    
    /**
     * 源数据库名称.
     */
    @Schema(name = "sourceDatabaseName", description = "源数据库名称")
    private String sourceDatabaseName;
    
    /**
     * 目标数据库名称.
     */
    @Schema(name = "targetDatabaseName", description = "目标数据库名称")
    private String targetDatabaseName;
    
    /**
     * 源数据库名称.
     */
    @Schema(name = "sourceSchemaName", description = "源数据库schema名称")
    private String sourceSchemaName;
    
    /**
     * 源数据库名称.
     */
    @Schema(name = "targetSchemaName", description = "目标数据库schema名称")
    private String targetSchemaName;
    
    /**
     * 源数据源名称.
     */
    @Schema(name = "sourceDataSourceName", description = "源数据源名称")
    private String sourceDataSourceName;
    
    /**
     * 目标数据源名称.
     */
    @Schema(name = "targetDataSourceName", description = "目标数据源名称")
    private String targetDataSourceName;
    
    /**
     * 操作日期.
     */
    @Schema(name = "operateTime", description = "操作日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operateTime;
    
    /**
     * 范围快照.
     */
    @Schema(name = "snapshotStep1", description = "范围快照")
    private JsonNode snapshotStep1;
    
    /**
     * 对比快照.
     */
    @Schema(name = "snapshotStep2", description = "对比快照")
    private JsonNode snapshotStep2;
    
    /**
     * 执行快照.
     */
    @Schema(name = "snapshotStep3", description = "执行快照")
    private JsonNode snapshotStep3;
}
