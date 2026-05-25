package com.datafusion.manager.development.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 开发侧SQL异步任务标识.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Data
@Schema(name = "ExecSqlJobIdDto", description = "开发侧SQL异步任务标识")
public class ExecSqlJobIdDto {

    /**
     * 任务ID.
     */
    @Schema(name = "jobId", description = "任务ID")
    @NotNull(message = "jobId不能为空")
    private UUID jobId;
}
