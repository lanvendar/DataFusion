package com.datafusion.manager.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 建表语句执行明细.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@Data
@Accessors(chain = true)
@Schema(name = "ExecuteCreateTableDetailDto", description = "建表语句执行明细")
public class ExecuteCreateTableDetailDto {

    /**
     * 表名.
     */
    @Schema(name = "tableName", description = "建表表名")
    private String tableName;

    /**
     * 建表语句.
     */
    @Schema(name = "init_db", description = "建表语句")
    private String sql;

    /**
     * 执行状态.
     */
    @Schema(name = "success", description = "执行状态")
    private Boolean success;

    /**
     * 执行信息.
     */
    @Schema(name = "message", description = "执行信息")
    private String message;
}
