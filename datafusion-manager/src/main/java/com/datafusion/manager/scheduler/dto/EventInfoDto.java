package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 调度-事件响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/4/1
 * @since 1.0.0
 */
@Data
@Schema(name = "EventInfoDto", description = "事件响应Dto")
public class EventInfoDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 事件名称.
     */
    @Schema(name = "eventName", description = "事件名称")
    private String eventName;

    /**
     * 事件类型.
     */
    @Schema(name = "eventType", description = "事件类型")
    private String eventType;

    /**
     * 关联流程ID.
     */
    @Schema(name = "flowId", description = "关联流程ID")
    private UUID flowId;

    /**
     * 关联任务ID.
     */
    @Schema(name = "taskId", description = "关联任务ID")
    private UUID taskId;

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
