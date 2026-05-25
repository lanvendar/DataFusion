package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 库表资源响应体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/02/28
 * @since 2026/02/28
 */
@Data
public class TableResourceResp {

    /**
     * 资源ID.
     */
    @Schema(name = "resourceId", description = "资源ID")
    private UUID resourceId;

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
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;

    /**
     * 表描述.
     */
    @Schema(name = "tableDesc", description = "表描述")
    private String tableDesc;

    /**
     * 表字段.
     */
    @Schema(name = "columns", description = "表字段")
    private List<ColumnResp> columns;

    /**
     * 列信息响应.
     */
    @Data
    public static class ColumnResp {
        /**
         * 列名称.
         */
        @Schema(name = "columnName", description = "列名称")
        private String columnName;

        /**
         * 列描述.
         */
        @Schema(name = "columnDesc", description = "列描述")
        private String columnDesc;

        /**
         * 列类型.
         */
        @Schema(name = "columnType", description = "列类型")
        private String columnType;
    }

}
