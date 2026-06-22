package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-worker 注册修改Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
@Schema(name = "WorkerRegistryUpdateDto", description = "worker 注册修改Dto")
public class WorkerRegistryUpdateDto {

    /**
     * worker 注册记录ID.
     */
    @Schema(name = "id", description = "worker 注册记录ID")
    @NotNull(message = "worker注册记录ID不能为空")
    private UUID id;

    /**
     * worker 编码.
     */
    @Schema(name = "workerCode", description = "worker 编码")
    private String workerCode;

    /**
     * 主机名称.
     */
    @Schema(name = "hostName", description = "主机名称")
    private String hostName;

    /**
     * IP 地址.
     */
    @Schema(name = "host", description = "IP 地址")
    private String host;

    /**
     * 端口.
     */
    @Schema(name = "port", description = "端口")
    private Integer port;

    /**
     * 区域/分组.
     */
    @Schema(name = "zone", description = "区域/分组")
    private String zone;

    /**
     * 插件类型列表.
     */
    @Schema(name = "plugins", description = "插件类型列表，逗号分隔")
    private String plugins;

    /**
     * 是否有效.
     */
    @Schema(name = "isActive", description = "是否有效: 1-有效 0-无效")
    private Integer isActive;

    /**
     * 资源说明.
     */
    @Schema(name = "remark", description = "资源说明")
    private String remark;
}
