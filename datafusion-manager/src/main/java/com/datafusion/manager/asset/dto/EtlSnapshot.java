package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/11
 * @since 2025/10/11
 */
@Data
@Accessors(chain = true)
public class EtlSnapshot {
    
    /**
     * 数据源id.
     */
    @Schema(name = "datasourceId", description = "datasourceId")
    private UUID datasourceId;
    
    /**
     * 数据连接名称.
     */
    @Schema(name = "datasourceName", description = "数据连接名称")
    private String datasourceName;
    
    /**
     * 数据库类型.
     */
    @Schema(name = "databaseType", description = "数据库类型")
    private String databaseType;
    
    /**
     * 数据库schema名称.
     */
    @Schema(name = "schemaName", description = "数据库schema名称")
    private String schemaName;
    
    /**
     * 数据库名称.
     */
    @Schema(name = "databaseName", description = "数据库名称")
    private String databaseName;
    
    /**
     * 文件路径.
     */
    @Schema(name = "filePath", description = "文件路径")
    private String filePath;
    
    /**
     * 文件名称.
     */
    @Schema(name = "fileName", description = "文件名称")
    private String fileName;
    
    /**
     * dag名称.
     */
    @Schema(name = "dagName", description = "etl dag名称")
    private String dagName;
    
    /**
     * 任务名称.
     */
    @Schema(name = "taskName", description = "etl 任务名称")
    private String taskName;
    
    /**
     * sql 脚本.
     */
    @Schema(name = "init_db", description = "etl sql脚本")
    private String sql;
    
}
