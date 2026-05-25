package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-变量修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/30
 * @since 1.0.0
 */
@Data
@Schema(name = "VariableInfoUpdateDto", description = "变量修改Dto")
public class VariableInfoUpdateDto {

    /**
     * 变量ID.
     */
    @Schema(name = "id", description = "变量ID")
    @NotNull(message = "变量ID不能为空")
    private UUID id;

    /**
     * 变量编码(SYSTEM类型忽略).
     */
    @Schema(name = "code", description = "变量编码(SYSTEM类型忽略)")
    private String code;

    /**
     * 变量名称(SYSTEM类型忽略).
     */
    @Schema(name = "name", description = "变量名称(SYSTEM类型忽略)")
    private String name;

    /**
     * 值类型(SYSTEM类型忽略).
     */
    @Schema(name = "valueType", description = "值类型: STRING / EXPRESSION(SYSTEM类型忽略)")
    private String valueType;

    /**
     * 值(SYSTEM和CUSTOM均可修改).
     */
    @Schema(name = "value", description = "值(SYSTEM和CUSTOM均可修改)")
    private String value;
}
