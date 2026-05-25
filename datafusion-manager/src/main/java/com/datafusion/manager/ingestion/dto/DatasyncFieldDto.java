package com.datafusion.manager.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

/**
 * 数据同步字段映射DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@Schema(name = "DatasyncFieldDto", description = "数据同步字段映射DTO")
public class DatasyncFieldDto {

    /**
     * 数据来源类型(SOURCE/TARGET).
     */
    @Schema(name = "sourceTarget", description = "数据来源类型(SOURCE/TARGET)")
    @NotBlank(message = "sourceTarget不能为空")
    private String sourceTarget;

    /**
     * 列名.
     */
    @Schema(name = "columnName", description = "列名")
    @NotBlank(message = "列名不能为空")
    private String columnName;

    /**
     * 数据类型.
     */
    @Schema(name = "dataType", description = "数据类型")
    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    /**
     * 序号.
     */
    @Schema(name = "columnIndex", description = "序号")
    private Integer columnIndex;
}