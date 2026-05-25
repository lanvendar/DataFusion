package com.datafusion.manager.development.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * SQL脚本任务修改DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Data
@Accessors(chain = true)
@Schema(name = "DevelopScriptSqlTaskUpdateDto", description = "SQL脚本任务修改DTO")
public class DevelopScriptSqlTaskUpdateDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    @NotNull(message = "任务ID不能为空")
    private UUID id;

    /**
     * 任务名称.
     */
    @Schema(name = "name", description = "任务名称")
    private String name;

    /**
     * 任务编码.
     */
    @Schema(name = "code", description = "任务编码")
    private String code;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * SQL脚本（库表 jsonb）.
     */
    @Schema(name = "script", description = "SQL脚本(jsonb)，如 JSON 字符串或结构化 JSON；不传则不修改")
    private JsonNode script;

    /**
     * 变量(JSON).
     */
    @Schema(name = "variables", description = "变量(JSON)")
    private JsonNode variables;

    /**
     * 任务分组ID.
     */
    @Schema(name = "groupId", description = "任务分组ID")
    private UUID groupId;
}
