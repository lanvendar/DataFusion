package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 调度-流程发布请求Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@Data
@Schema(name = "FlowPublishDto", description = "流程发布请求Dto")
public class FlowPublishDto {

    /**
     * 流程ID.
     */
    @Schema(name = "id", description = "流程ID")
    @NotNull(message = "流程ID不能为空")
    private UUID id;

    /**
     * 是否同时启用调度, 默认false.
     */
    @Schema(name = "enableSchedule", description = "是否同时启用调度, 默认false")
    private Boolean enableSchedule;
}
