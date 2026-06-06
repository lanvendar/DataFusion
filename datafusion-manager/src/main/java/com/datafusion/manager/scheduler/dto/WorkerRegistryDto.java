package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 调度-worker 注册响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
@Schema(name = "WorkerRegistryDto", description = "worker 注册响应Dto")
public class WorkerRegistryDto {

    /**
     * 主键.
     */
    @Schema(name = "id", description = "主键")
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
     * worker 状态.
     */
    @Schema(name = "status", description = "worker 状态: 0-下线 1-上线 2-清除")
    private Integer status;

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
     * 注册时间.
     */
    @Schema(name = "registerTime", description = "注册时间")
    private Date registerTime;

    /**
     * 最近心跳时间.
     */
    @Schema(name = "lastHeartbeatTime", description = "最近心跳时间")
    private Date lastHeartbeatTime;

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
