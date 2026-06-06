package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 调度-流程实例任务查询Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowInstanceTaskQueryDto", description = "流程实例任务查询Dto")
public class FlowInstanceTaskQueryDto {

    /**
     * 流程实例ID.
     */
    @Schema(name = "flowInstanceId", description = "流程实例ID")
    private UUID flowInstanceId;

    /**
     * 查询视图类型.
     */
    @Schema(name = "viewType", description = "REALTIME查询实时表，HISTORY查询历史表")
    private String viewType;
}
