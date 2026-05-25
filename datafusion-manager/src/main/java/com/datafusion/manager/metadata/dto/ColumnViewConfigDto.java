package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 字段配置页面信息Dto.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/5
 * @since 2025/9/5
 */
@Data
@AllArgsConstructor
@Schema(name = "ColumnViewConfigDto", description = "字段配置页面信息Dto")
public class ColumnViewConfigDto {

    /**
     * 字段精度.
     */
    @Schema(name = "displayLength", description = "字段长度")
    private int displayLength;

    /**
     * 字段精度.
     */
    @Schema(name = "displayPrecision", description = "字段精度")
    private int displayPrecision;

    /**
     * 字段精度.
     */
    @Schema(name = "displayScale", description = "数字类精度")
    private int displayScale;

}
