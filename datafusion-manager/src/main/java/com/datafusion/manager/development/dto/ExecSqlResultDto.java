package com.datafusion.manager.development.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 开发侧SQL执行结果.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/22
 * @since 2026/4/22
 */
@Data
@Schema(name = "ExecSqlResultDto", description = "开发侧SQL执行结果")
public class ExecSqlResultDto {

    /**
     * 结果集列名.
     */
    @Schema(name = "columns", description = "结果集列名")
    private List<String> columns;

    /**
     * 结果集行数据.
     */
    @Schema(name = "rows", description = "结果集行数据")
    private List<Map<String, Object>> rows;

    /**
     * 结果行数.
     */
    @Schema(name = "rowCount", description = "结果行数")
    private Integer rowCount;

    /**
     * 执行信息.
     */
    @Schema(name = "message", description = "执行信息")
    private String message;
}
