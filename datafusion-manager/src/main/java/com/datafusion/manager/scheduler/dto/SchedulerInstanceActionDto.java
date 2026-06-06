package com.datafusion.manager.scheduler.dto;

import lombok.Data;

import java.util.UUID;

/**
 * 调度实例操作请求.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
public class SchedulerInstanceActionDto {

    /**
     * 流程实例 ID.
     */
    private UUID flowInstanceId;

    /**
     * 任务实例 ID.
     */
    private UUID taskInstanceId;

    /**
     * 操作类型.
     */
    private String actionType;
}
