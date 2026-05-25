package com.datafusion.manager.asset.dto;

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
@Schema(name = "ColumnNodeDto", description = "字段级dto")
public class ColumnNodeDto {
    
    /**
     * 字段节点名称.
     */
    @Schema(name = "columnNodeName", description = "字段节点名称")
    private String columnNodeName;
    
    /**
     * 字段节点urn.
     */
    @Schema(name = "columnNodeUrn", description = "字段节点urn")
    private String columnNodeUrn;
    
    
}
