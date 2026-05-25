package com.datafusion.manager.development.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * SQL脚本任务新增DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Data
@Accessors(chain = true)
@Schema(name = "DevelopScriptSqlTaskSaveDto", description = "SQL脚本任务新增DTO")
public class DevelopScriptSqlTaskSaveDto {

    /**
     * 任务名称.
     */
    @Schema(name = "name", description = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /**
     * 任务编码.
     */
    @Schema(name = "code", description = "任务编码，如KF2401010001；不传或空则按 KF+yyMMdd+序号 自动生成")
    private String code;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * SQL脚本（库表 jsonb NOT NULL）.
     */
    @Schema(name = "script", description = "SQL脚本(jsonb)，JSON 字符串或对象均可")
    @NotNull(message = "SQL脚本不能为空")
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
