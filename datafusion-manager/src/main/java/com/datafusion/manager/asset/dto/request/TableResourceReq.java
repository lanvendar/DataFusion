package com.datafusion.manager.asset.dto.request;

import com.datafusion.manager.metadata.dto.TableColumnsTreeDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 库表资源导入请求体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/02/28
 * @since 2026/02/28
 */
@Data
public class TableResourceReq {

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
     * 数据源表信息.
     */
    @NotEmpty(message = "tableColumns不能为空")
    @Schema(name = "tableColumns", description = "库表信息")
    private List<TableColumnsTreeDto> tableColumns;

}
