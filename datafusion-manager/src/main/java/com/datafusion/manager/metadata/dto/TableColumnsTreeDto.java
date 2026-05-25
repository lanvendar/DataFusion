package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/9
 * @since 2025/9/9
 */
@Data
@Accessors(chain = true)
@Schema(name = "TableColumnsDto", description = "返回表字段树形结构")
public class TableColumnsTreeDto {
    
    /**
     * 表名.
     */
    @Schema(name = "tableId", description = "表id")
    private UUID tableId;
    
    /**
     * 表名.
     */
    @Schema(name = "tableName", description = "表名")
    private String tableName;
    
    /**
     * 表中文名.
     */
    @Schema(name = "tableDesc", description = "表中文名")
    private String tableDesc;
    
    /**
     * 字段列表.
     */
    @Schema(name = "columns", description = "表字段")
    private List<ColumnTreeDto> columns;
}
