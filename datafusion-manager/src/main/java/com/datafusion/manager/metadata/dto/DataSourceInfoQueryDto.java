package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据源查询参数DTO.
 *
 * @author david
 * @version 3.6.4, 2024/8/15
 * @since 3.6.4, 2024/8/15
 */
@Data
@Schema(name = "DataSourceInfoQueryDto", description = "数据源查询参数DTO")
public class DataSourceInfoQueryDto {
    
    /**
     * 数据连接名称.
     */
    @Schema(name = "name", description = "数据连接名称")
    private String name;
    
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
    
}
