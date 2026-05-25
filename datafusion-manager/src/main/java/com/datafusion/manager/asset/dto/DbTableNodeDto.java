package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 库表资源导入请求体.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Data
public class DbTableNodeDto {

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
     * 数据源表信息.
     */
    @NotEmpty(message = "tableColumns不能为空")
    @Schema(name = "tableColumns", description = "库表信息")
    List<TableColumnsNodeDto> tableColumns;
    
}
