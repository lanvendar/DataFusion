package com.datafusion.manager.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 建表语句执行结果.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@Data
@Accessors(chain = true)
@Schema(name = "ExecuteCreateTableResultDto", description = "建表语句执行结果")
public class ExecuteCreateTableResultDto {

    /**
     * 执行是否成功.
     */
    @Schema(name = "success", description = "执行是否成功")
    private Boolean success;

    /**
     * 总语句数.
     */
    @Schema(name = "totalCount", description = "总语句数")
    private Integer totalCount;

    /**
     * 执行成功数量.
     */
    @Schema(name = "executedCount", description = "执行成功数量")
    private Integer executedCount;

    /**
     * 语句执行明细.
     */
    @Schema(name = "details", description = "语句执行明细")
    private List<ExecuteCreateTableDetailDto> details;
}
