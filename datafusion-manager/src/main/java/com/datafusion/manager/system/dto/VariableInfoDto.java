package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 系统-变量响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/30
 * @since 1.0.0
 */
@Data
@Schema(name = "VariableInfoDto", description = "变量响应Dto")
public class VariableInfoDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 变量编码.
     */
    @Schema(name = "code", description = "变量编码")
    private String code;

    /**
     * 变量名称.
     */
    @Schema(name = "name", description = "变量名称")
    private String name;

    /**
     * 变量类型: CUSTOM / SYSTEM.
     */
    @Schema(name = "type", description = "变量类型: CUSTOM / SYSTEM")
    private String type;

    /**
     * 值类型: STRING / EXPRESSION.
     */
    @Schema(name = "valueType", description = "值类型: STRING / EXPRESSION")
    private String valueType;

    /**
     * 值.
     */
    @Schema(name = "value", description = "值")
    private String value;

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String creator;

    /**
     * 修改人.
     */
    @Schema(name = "updater", description = "修改人")
    private String updater;

    /**
     * 创建时间.
     */
    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    /**
     * 修改时间.
     */
    @Schema(name = "updateTime", description = "修改时间")
    private Date updateTime;
}
