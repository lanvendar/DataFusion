package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/9
 * @since 2025/9/9
 */

@Data
@Accessors(chain = true)
@Schema(name = "ColumnTreeDto", description = "返回表字段树形结构")
public class ColumnTreeDto {
    
    /**
     * 字段名.
     */
    @Schema(name = "columnName", description = "字段名")
    private String columnName;
    
    /**
     * 字段描述.
     */
    @Schema(name = "columnDesc", description = "字段中文名")
    private String columnDesc;
    
    /**
     * 字段类型.
     */
    @Schema(name = "columnType", description = "字段类型,比如varchar")
    private String columnType;
    
    /**
     * 字段全类型.
     */
    @Schema(name = "columnFullType", description = "字段类型,比如varchar(55)")
    private String columnFullType;
    
    
}
