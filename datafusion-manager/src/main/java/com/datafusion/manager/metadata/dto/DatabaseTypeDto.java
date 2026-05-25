package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 数据源来源类型.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/12
 * @since 3.7.2, 2024/11/12
 */
@Data
@AllArgsConstructor
public class DatabaseTypeDto {

    /**
     * 类型key.
     */
    @Schema(description = "类型key")
    private String type;

    /**
     * 类型名称.
     */
    @Schema(description = "类型名称")
    private String name;
}
