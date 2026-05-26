package com.datafusion.manager.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 按数据源执行建表语句请求.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@Data
@Schema(name = "ExecuteCreateTableDto", description = "按数据源执行建表语句请求")
public class ExecuteCreateTableDto {

    /**
     * 数据源ID.
     */
    @Schema(name = "datasourceId", description = "数据源ID")
    @NotNull(message = "datasourceId不能为空")
    private UUID datasourceId;

    /**
     * 建表语句.
     */
    @Schema(name = "init_db", description = "建表SQL脚本")
    @NotEmpty(message = "sql不能为空")
    private String sql;
}
