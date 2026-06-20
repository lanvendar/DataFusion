package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统-变量查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/30
 * @since 1.0.0
 */
@Data
@Schema(name = "VariableInfoQueryDto", description = "变量查询条件Dto")
public class VariableInfoQueryDto {

    /**
     * 变量名称(模糊查询).
     */
    @Schema(name = "name", description = "变量名称(模糊查询)")
    private String name;

    /**
     * 变量编码(模糊查询).
     */
    @Schema(name = "code", description = "变量编码(模糊查询)")
    private String code;

    /**
     * 变量类型过滤: CUSTOM / SYSTEM.
     */
    @Schema(name = "type", description = "变量类型过滤: CUSTOM / SYSTEM")
    private String type;

    /**
     * 值类型过滤: STRING / LONG / EXPRESSION.
     */
    @Schema(name = "valueType", description = "值类型过滤: STRING / LONG / EXPRESSION")
    private String valueType;
}
