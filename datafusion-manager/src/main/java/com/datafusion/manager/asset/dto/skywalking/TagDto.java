package com.datafusion.manager.asset.dto.skywalking;

import lombok.Data;

/**
 * TagDto(链路追踪 Span 内部的 Tag 键值对).
 *
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class TagDto {
    
    /**
     * key.
     */
    private String key;
    
    /**
     * value.
     */
    private String value;
}
