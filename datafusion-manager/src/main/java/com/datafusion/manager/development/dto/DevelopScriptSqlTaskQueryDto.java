package com.datafusion.manager.development.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * SQL脚本任务查询条件DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Data
@Accessors(chain = true)
@Schema(name = "DevelopScriptSqlTaskQueryDto", description = "SQL脚本任务查询条件DTO")
public class DevelopScriptSqlTaskQueryDto {

    /**
     * 任务名称（模糊）.
     */
    @Schema(name = "name", description = "任务名称，模糊匹配")
    private String name;

    /**
     * 任务编码（模糊）.
     */
    @Schema(name = "code", description = "任务编码，模糊匹配")
    private String code;

    /**
     * 任务分组ID（精确）.
     */
    @Schema(name = "groupId", description = "任务分组ID，精确匹配")
    private UUID groupId;

    /**
     * 是否已发布（精确）；null表示不限.
     */
    @Schema(name = "publishStatus", description = "是否已发布，精确匹配；为空表示不限")
    private Boolean publishStatus;
}
