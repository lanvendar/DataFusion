package com.datafusion.manager.ingestion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.UUID;

/**
 * 数据集成-任务定义响应DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@Schema(name = "IngestionTaskDefinitionDto", description = "数据集成-任务定义响应DTO")
public class IngestionTaskDefinitionDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 任务名称.
     */
    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    /**
     * 任务编码.
     */
    @Schema(name = "taskCode", description = "任务编码，如JC2401010001")
    private String taskCode;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 任务类型id.
     */
    @Schema(name = "taskTypeId", description = "任务类型id")
    private String taskTypeId;

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 任务参数(JSON).
     */
    @Schema(name = "taskParam", description = "任务参数(JSON)")
    private JsonNode taskParam;

    /**
     * 任务定义(JSON).
     */
    @Schema(name = "definition", description = "任务定义(JSON)")
    private JsonNode definition;

    /**
     * 版本号.
     */
    @Schema(name = "version", description = "版本号")
    private Integer version;

    /**
     * 删除标识.
     */
    @Schema(name = "modelStatus", description = "删除标识(0=正常,1=删除)")
    private Short modelStatus;

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
     * 工作流id.
     */
    @Schema(name = "flowId", description = "工作流id")
    private UUID flowId;

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