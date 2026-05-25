package com.datafusion.manager.asset.dto;

import com.datafusion.manager.metadata.dto.ColumnTreeDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Data
public class DbTableSnapshot {
    
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
     * 表字段.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;
    
    /**
     * 表描述.
     */
    @Schema(name = "tableDesc", description = "表描述")
    private String tableDesc;
    
    /**
     * 表id.
     */
    @Schema(name = "tableId", description = "表id")
    private String tableId;
    
    /**
     * 表字段.
     */
    @Schema(name = "columns", description = "表字段")
    private List<ColumnTreeDto> columns;
    
}
