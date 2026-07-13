package com.datafusion.manager.scheduler.dto;

import com.datafusion.manager.scheduler.model.BusinessSourceRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 调度-任务响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInfoDto", description = "任务响应Dto")
public class TaskInfoDto {

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
    @Schema(name = "taskCode", description = "任务编码")
    private String taskCode;

    /**
     * 任务描述.
     */
    @Schema(name = "description", description = "任务描述")
    private String description;

    /**
     * 任务类型ID.
     */
    @Schema(name = "taskTypeId", description = "任务类型ID")
    private String taskTypeId;

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 任务变量参数(JSON).
     */
    @Schema(name = "taskParam", description = "任务变量参数(JSON)")
    private String taskParam;

    /**
     * 任务定义(JSON).
     */
    @Schema(name = "definition", description = "任务定义(JSON)")
    private String definition;

    /**
     * 是否绑定流程.
     */
    @Schema(name = "isBound", description = "是否绑定流程")
    private Boolean isBound;

    /**
     * 流程ID.
     */
    @Schema(name = "flowId", description = "流程ID")
    private UUID flowId;

    /**
     * 执行组件ID.
     */
    @Schema(name = "pluginId", description = "执行组件ID")
    private UUID pluginId;

    /**
     * 任务前端视图(JSON).
     */
    @Schema(name = "view", description = "任务前端视图(JSON)")
    private String view;

    /**
     * 依赖事件ID.
     */
    @Schema(name = "depEventIds", description = "依赖事件ID")
    private String depEventIds;

    /**
     * 事件ID.
     */
    @Schema(name = "eventId", description = "事件ID")
    private UUID eventId;

    /**
     * 是否启用.
     */
    @Schema(name = "enabled", description = "是否启用")
    private Boolean enabled;

    /**
     * 任务同步标识.
     */
    @Schema(name = "syncFlag", description = "任务同步标识")
    private Boolean syncFlag;

    /**
     * 业务来源定位信息.
     */
    @Schema(name = "sourceRoute", description = "业务来源定位信息")
    private BusinessSourceRoute sourceRoute;

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
