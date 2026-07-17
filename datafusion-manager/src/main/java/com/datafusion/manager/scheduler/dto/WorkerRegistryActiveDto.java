package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-worker 启停请求.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Data
@Schema(name = "WorkerRegistryActiveDto", description = "worker 启用或禁用请求")
public class WorkerRegistryActiveDto {

    /**
     * worker 注册记录ID.
     */
    @Schema(name = "id", description = "worker 注册记录ID")
    @NotNull(message = "worker注册记录ID不能为空")
    private UUID id;

    /**
     * 是否参与新任务调度.
     */
    @Schema(name = "isActive", description = "是否参与新任务调度: 1-启用 0-禁用")
    @NotNull(message = "worker有效标记不能为空")
    private Integer isActive;
}
