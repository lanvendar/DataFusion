package com.datafusion.manager.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 数据同步任务新增DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@Schema(name = "DatasyncTaskSaveDto", description = "数据同步任务新增DTO")
public class DatasyncTaskSaveDto {

    /**
     * 任务名称.
     */
    @Schema(name = "name", description = "任务名称")
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 来源数据源类型.
     */
    @Schema(name = "sourceDsType", description = "来源数据源类型")
    @NotBlank(message = "来源数据源类型不能为空")
    private String sourceDsType;

    /**
     * 来源数据源ID.
     */
    @Schema(name = "sourceDsId", description = "来源数据源ID")
    private UUID sourceDsId;

    /**
     * 来源实体ID.
     */
    @Schema(name = "sourceEntityId", description = "来源实体ID")
    private UUID sourceEntityId;

    /**
     * 来源数据源配置(JSON).
     */
    @Schema(name = "sourceConfig", description = "来源数据源配置(JSON)")
    private JsonNode sourceConfig;

    /**
     * 目标数据源类型.
     */
    @Schema(name = "targetDsType", description = "目标数据源类型")
    @NotBlank(message = "目标数据源类型不能为空")
    private String targetDsType;

    /**
     * 目标数据源ID.
     */
    @Schema(name = "targetDsId", description = "目标数据源ID")
    @NotNull(message = "目标数据源ID不能为空")
    private UUID targetDsId;

    /**
     * 目标实体ID.
     */
    @Schema(name = "targetEntityId", description = "目标实体ID")
    private UUID targetEntityId;

    /**
     * 目标数据源配置(JSON).
     */
    @Schema(name = "targetConfig", description = "目标数据源配置(JSON)")
    private JsonNode targetConfig;

    /**
     * 变量(JSON).
     */
    @Schema(name = "variables", description = "变量(JSON)")
    private JsonNode variables;

    /**
     * 字段映射列表.
     */
    @Schema(name = "fields", description = "字段映射列表(querySql模式仅含TARGET侧)")
    private List<DatasyncFieldDto> fields;
}