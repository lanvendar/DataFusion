package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/30
 * @since 2025/10/30
 */

@Data
@Accessors(chain = true)
public class EdgeColumnInfoDto {
    
    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    private String tableName;
    
    /**
     * 字段名称.
     */
    @Schema(name = "columnName", description = "字段名称")
    private String columnName;
}
