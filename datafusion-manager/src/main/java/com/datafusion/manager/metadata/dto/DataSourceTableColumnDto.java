package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/23
 * @since 2025/10/23
 */
@Data
@AllArgsConstructor
public class DataSourceTableColumnDto {
    
    /**
     * 数据库名称.
     */
    @Schema(name = "datasourceName", description = "数据源名称")
    private String datasourceName;
    
    /**
     * 数据库id.
     */
    @Schema(name = "datasourceId", description = "数据库id")
    private String datasourceId;
    
    
    /**
     * 数据库类型.
     */
    @Schema(name = "databaseType", description = "数据库类型")
    private String databaseType;
    
    /**
     * 数据库名称.
     */
    @Schema(name = "databaseName", description = "数据库名称")
    private String databaseName;
    
    /**
     * 数据库schema名称.
     */
    @Schema(name = "schemaName", description = "数据库schema名称")
    private String schemaName;
    
    /**
     * 表名.
     */
    @Schema(name = "tableName", description = "表名")
    private String tableName;
    
    /**
     * 字段名.
     */
    @Schema(name = "columnName", description = "字段名")
    private String columnName;
    
    /**
     * 字段类型.
     */
    @Schema(name = "columnType", description = "字段类型")
    private String columnType;
    
    /**
     * 名.
     */
    @Schema(name = "columnJavaType", description = "字段Java类型")
    private String columnJavaType;
    
    
}
