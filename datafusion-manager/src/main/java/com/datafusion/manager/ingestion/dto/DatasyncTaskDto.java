package com.datafusion.manager.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 数据同步任务响应DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@Schema(name = "DatasyncTaskDto", description = "数据同步任务响应DTO")
public class DatasyncTaskDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 任务名称.
     */
    @Schema(name = "name", description = "任务名称")
    private String name;

    /**
     * 任务编码.
     */
    @Schema(name = "code", description = "任务编码，如JC2401010001")
    private String code;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 来源数据源类型.
     */
    @Schema(name = "sourceDsType", description = "来源数据源类型")
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
    private String targetDsType;

    /**
     * 目标数据源ID.
     */
    @Schema(name = "targetDsId", description = "目标数据源ID")
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
     * 发布状态.
     */
    @Schema(name = "publishStatus", description = "发布状态")
    private Boolean publishStatus;

    /**
     * 发布时间.
     */
    @Schema(name = "publishTime", description = "发布时间")
    private Date publishTime;

    /**
     * 变量(JSON).
     */
    @Schema(name = "variables", description = "变量(JSON)")
    private JsonNode variables;

    /**
     * 字段映射列表(仅详情查询返回).
     */
    @Schema(name = "fields", description = "字段映射列表(仅详情查询返回)")
    private List<DatasyncFieldDto> fields;

    /**
     * 创建人.
     */
    @Schema(name = "creator", description = "创建人")
    private String creator;

    /**
     * 创建时间.
     */
    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;
}