package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调度-worker 注册查询条件Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
@Schema(name = "WorkerRegistryQueryDto", description = "worker 注册查询条件Dto")
public class WorkerRegistryQueryDto {

    /**
     * worker 编码.
     */
    @Schema(name = "workerCode", description = "worker 编码(模糊查询)")
    private String workerCode;

    /**
     * 主机名称.
     */
    @Schema(name = "hostName", description = "主机名称(模糊查询)")
    private String hostName;

    /**
     * IP 地址.
     */
    @Schema(name = "host", description = "IP 地址(模糊查询)")
    private String host;

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
     * 是否有效.
     */
    @Schema(name = "isActive", description = "是否有效: 1-有效 0-无效")
    private Integer isActive;
}
