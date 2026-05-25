package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/23
 * @since 2025/9/23
 */
@Data
@AllArgsConstructor
public class KeyValueDto {
    
    /**
     * 前端展示使用.
     */
    @Schema(description = "前端展示值")
    private String key;
    
    /**
     * 后端实际需要值.
     */
    @Schema(description = "后端实际需要值")
    private String value;
    
}
