package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 系统-变量新增Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/30
 * @since 1.0.0
 */
@Data
@Schema(name = "VariableInfoSaveDto", description = "变量新增Dto")
public class VariableInfoSaveDto {

    /**
     * 变量编码(全局唯一).
     */
    @Schema(name = "code", description = "变量编码(全局唯一)")
    @NotBlank(message = "变量编码不能为空")
    private String code;

    /**
     * 变量名称.
     */
    @Schema(name = "name", description = "变量名称")
    @NotBlank(message = "变量名称不能为空")
    private String name;

    /**
     * 值类型: STRING / EXPRESSION.
     */
    @Schema(name = "valueType", description = "值类型: STRING / EXPRESSION")
    @NotBlank(message = "值类型不能为空")
    private String valueType;

    /**
     * 值.
     */
    @Schema(name = "value", description = "值")
    private String value;
}
