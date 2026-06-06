package com.datafusion.manager.scheduler.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.datafusion.common.spring.po.BaseIdEntity;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 调度 worker 注册表实体.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
@TableName("scheduler_worker_registry")
public class WorkerRegistryEntity extends BaseIdEntity {

    /**
     * worker 编码.
     */
    @TableField("worker_code")
    private String workerCode;

    /**
     * 主机名称.
     */
    @TableField("host_name")
    private String hostName;

    /**
     * IP 地址或主机地址.
     */
    @TableField("host")
    private String host;

    /**
     * 端口.
     */
    @TableField("port")
    private Integer port;

    /**
     * worker 状态.
     */
    @TableField("status")
    private Integer status;

    /**
     * 区域或分组.
     */
    @TableField("zone")
    private String zone;

    /**
     * 插件类型列表.
     */
    @TableField("plugins")
    private String plugins;

    /**
     * 注册时间.
     */
    @TableField("register_time")
    private Date registerTime;

    /**
     * 最近心跳时间.
     */
    @TableField("last_heartbeat_time")
    private Date lastHeartbeatTime;

    /**
     * 是否有效.
     */
    @TableField("is_active")
    private Integer isActive;

    /**
     * 资源说明.
     */
    @TableField("remark")
    private String remark;

    /**
     * 租户 ID.
     */
    @TableField("tenant_id")
    private UUID tenantId;
}
