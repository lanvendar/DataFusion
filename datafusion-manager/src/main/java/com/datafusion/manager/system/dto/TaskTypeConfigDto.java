package com.datafusion.manager.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 系统-任务类型配置响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskTypeConfigDto", description = "任务类型配置响应Dto")
public class TaskTypeConfigDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
    private UUID id;

    /**
     * 任务类型.
     */
    @Schema(name = "taskType", description = "任务类型")
    private String taskType;

    /**
     * 默认插件ID.
     */
    @Schema(name = "defaultPluginId", description = "默认插件ID")
    private UUID defaultPluginId;

    /**
     * 插件类型.
     */
    @Schema(name = "pluginType", description = "插件类型")
    private String pluginType;

    /**
     * 租户ID.
     */
    @Schema(name = "tenantId", description = "租户ID")
    private UUID tenantId;

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
