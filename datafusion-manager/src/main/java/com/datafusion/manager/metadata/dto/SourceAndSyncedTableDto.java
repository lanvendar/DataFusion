package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 数据源表和已同步的表.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/5
 * @since 3.7.2, 2024/11/5
 */
@Data
@AllArgsConstructor
public class SourceAndSyncedTableDto {
    /**
     * 数据源表集合.
     */
    @Schema(name = "sourceTables", description = "数据源表集合")
    private List<TableInfoLiteDto> sourceTables;

    /**
     * 已同步表集合.
     */
    @Schema(name = "sourceTables", description = "已同步表集合")
    private List<TableInfoLiteDto> syncedTables;
}
