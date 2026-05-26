package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * ETL资源导入请求体.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/11
 * @since 2025/10/11
 */
@Data
public class EtlResourceDto {
    
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
    @NotNull(message = "schemaName不能为空")
    private String schemaName;
    
    /**
     * 数据库名称.
     */
    @Schema(name = "databaseName", description = "数据库名称")
    @NotNull(message = "databaseName不能为空")
    private String databaseName;
    
    /**
     * etl sql脚本.
     */
    @Schema(name = "init_db", description = "etl脚本")
    @NotNull(message = "sql不能为空")
    private EtlSnapshot sql;
    
    /**
     * etl 资源名称.
     */
    @Schema(name = "etlResourceName", description = "etl脚本资源名称")
    @NotNull(message = "etlResourceName不能为空")
    private String etlResourceName;
    
}
