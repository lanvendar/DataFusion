package com.datafusion.manager.asset.dto.request;

import com.datafusion.manager.asset.dto.EtlSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * ETL资源导入请求体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/03/03
 * @since 2026/03/03
 */
@Data
public class EtlResourceReq {

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
    @NotNull(message = "schemaName不能为空")
    @Schema(name = "schemaName", description = "数据库schema名称")
    private String schemaName;

    /**
     * 数据库名称.
     */
    @NotNull(message = "databaseName不能为空")
    @Schema(name = "databaseName", description = "数据库名称")
    private String databaseName;

    /**
     * etl sql脚本.
     */
    @Schema(name = "sql", description = "etl脚本")
    @NotNull(message = "sql不能为空")
    private EtlSnapshot sql;

    /**
     * etl 资源名称.
     */
    @Schema(name = "etlResourceName", description = "etl脚本资源名称")
    @NotNull(message = "etlResourceName不能为空")
    private String etlResourceName;

}
