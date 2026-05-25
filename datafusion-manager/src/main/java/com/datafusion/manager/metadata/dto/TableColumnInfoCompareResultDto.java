package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * TableColumnInfoCompareResultDto.
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
@Data
@AllArgsConstructor
public class TableColumnInfoCompareResultDto {
    /**
     * 原表字段集合.
     */
    @Schema(name = "sourceColumns", description = "原表字段集合")
    private List<TableColumnInfoCompareInfo> sourceColumns;
    
    /**
     * 目标字段集合.
     */
    @Schema(name = "targetTables", description = "目标字段集合")
    private List<TableColumnInfoCompareInfo> targetColumns;
}
