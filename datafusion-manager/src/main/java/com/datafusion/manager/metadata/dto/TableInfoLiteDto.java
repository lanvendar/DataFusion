package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数据源-数据表.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/4
 * @since 3.7.2, 2024/11/4
 */
@Data
public class TableInfoLiteDto {
    
    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;
    
    /**
     * 表注释.
     */
    @Schema(name = "tableDesc", description = "表注释")
    private String tableDesc;
    
    /**
     * 是否视图.
     */
    @Schema(name = "isView", description = "是否视图")
    private Boolean isView;
    
    /**
     * 表类型.
     */
    @Schema(name = "tableType", description = "表类型")
    private String tableType;
    
    /**
     * 是否已同步.
     */
    @Schema(name = "synced", description = "是否已同步")
    private boolean synced;
    
    /**
     * 表结构是否相同.
     */
    @Schema(name = "isEqual", description = "表结构是否相同")
    private Boolean isEqual;
}
