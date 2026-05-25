package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 调度-流程响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowInfoDto", description = "流程响应Dto")
public class FlowInfoDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 流程名称.
     */
    @Schema(name = "flowName", description = "流程名称")
    private String flowName;

    /**
     * 流程编码.
     */
    @Schema(name = "flowCode", description = "流程编码")
    private String flowCode;

    /**
     * 流程分组.
     */
    @Schema(name = "groupId", description = "流程分组")
    private UUID groupId;

    /**
     * 流程描述.
     */
    @Schema(name = "description", description = "流程描述")
    private String description;

    /**
     * 流程类型.
     */
    @Schema(name = "flowType", description = "流程类型")
    private String flowType;

    /**
     * 流程参数(JSON).
     */
    @Schema(name = "flowParam", description = "流程参数(JSON)")
    private String flowParam;

    /**
     * 调度开始时间.
     */
    @Schema(name = "startTime", description = "调度开始时间")
    private Long startTime;

    /**
     * 调度结束时间.
     */
    @Schema(name = "endTime", description = "调度结束时间")
    private Long endTime;

    /**
     * 是否调度.
     */
    @Schema(name = "enabled", description = "是否调度")
    private Boolean enabled;

    /**
     * 依赖事件ID列表.
     */
    @Schema(name = "depEventIds", description = "依赖事件ID列表")
    private List<String> depEventIds;

    /**
     * 事件ID.
     */
    @Schema(name = "eventId", description = "事件ID")
    private UUID eventId;

    /**
     * 发布状态.
     */
    @Schema(name = "publishState", description = "发布状态")
    private Boolean publishState;

    /**
     * 发布版本.
     */
    @Schema(name = "publishVersion", description = "发布版本")
    private Long publishVersion;

    /**
     * 触发器ID.
     */
    @Schema(name = "triggerId", description = "触发器ID")
    private UUID triggerId;

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
