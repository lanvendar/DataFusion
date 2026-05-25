package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/9
 * @since 2025/9/9
 */
@Data
@Accessors(chain = true)
@Schema(name = "TableColumnsNodeDto", description = "返回表字段树形结构")
public class TableColumnsNodeDto {
    
    /**
     * 表级节点名称.
     */
    @Schema(name = "tableNodeName", description = "表级节点名称")
    private String tableNodeName;
    
    /**
     * 表级节点urn.
     */
    @Schema(name = "tableNodeUrn", description = "表级节点urn")
    private String tableNodeUrn;
    
    /**
     * 字段列表.
     */
    @Schema(name = "columns", description = "表字段")
    private List<ColumnNodeDto> columns;
}
