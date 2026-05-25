package com.datafusion.manager.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 数据同步任务修改DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@Schema(name = "DatasyncTaskUpdateDto", description = "数据同步任务修改DTO")
public class DatasyncTaskUpdateDto {

    /**
     * 任务主键.
     */
    @Schema(name = "id", description = "任务主键")
    @NotNull(message = "id不能为空")
    private UUID id;

    /**
     * 任务名称(非必填).
     */
    @Schema(name = "name", description = "任务名称")
    private String name;

    /**
     * 任务描述(非必填).
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 来源数据源类型(非必填).
     */
    @Schema(name = "sourceDsType", description = "来源数据源类型")
    private String sourceDsType;

    /**
     * 来源数据源ID(非必填).
     */
    @Schema(name = "sourceDsId", description = "来源数据源ID")
    private UUID sourceDsId;

    /**
     * 来源实体ID(非必填).
     */
    @Schema(name = "sourceEntityId", description = "来源实体ID")
    private UUID sourceEntityId;

    /**
     * 来源数据源配置(非必填).
     */
    @Schema(name = "sourceConfig", description = "来源数据源配置(JSON)")
    private JsonNode sourceConfig;

    /**
     * 目标数据源类型(非必填).
     */
    @Schema(name = "targetDsType", description = "目标数据源类型")
    private String targetDsType;

    /**
     * 目标数据源ID(非必填).
     */
    @Schema(name = "targetDsId", description = "目标数据源ID")
    private UUID targetDsId;

    /**
     * 目标实体ID(非必填).
     */
    @Schema(name = "targetEntityId", description = "目标实体ID")
    private UUID targetEntityId;

    /**
     * 目标数据源配置(非必填).
     */
    @Schema(name = "targetConfig", description = "目标数据源配置(JSON)")
    private JsonNode targetConfig;

    /**
     * 变量(非必填).
     */
    @Schema(name = "variables", description = "变量(JSON)")
    private JsonNode variables;

    /**
     * 字段映射列表(非必填，传入则全量替换).
     */
    @Schema(name = "fields", description = "字段映射列表(传入则全量替换，querySql模式仅含TARGET侧)")
    private List<DatasyncFieldDto> fields;
}