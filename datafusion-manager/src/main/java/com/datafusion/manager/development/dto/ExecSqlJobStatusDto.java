package com.datafusion.manager.development.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 开发侧SQL异步任务状态.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Data
@Schema(name = "ExecSqlJobStatusDto", description = "开发侧SQL异步任务状态")
public class ExecSqlJobStatusDto {

    /**
     * 任务ID.
     */
    @Schema(name = "jobId", description = "任务ID")
    private UUID jobId;

    /**
     * 任务整体状态, PENDING/RUNNING/SUCCESS/FAILED/CANCELLED.
     */
    @Schema(name = "status", description = "任务整体状态")
    private String status;

    /**
     * 每段SQL的执行状态.
     */
    @Schema(name = "statements", description = "每段SQL的执行状态")
    private List<ExecSqlStatementStatusDto> statements;

    /**
     * 本次返回的日志增量.
     */
    @Schema(name = "logs", description = "日志增量")
    private List<ExecSqlLogLineDto> logs;

    /**
     * 下次拉取日志使用的偏移量.
     */
    @Schema(name = "logNextOffset", description = "下次拉取日志使用的偏移量")
    private Integer logNextOffset;

    /**
     * 最终结果集, 仅在状态稳定且存在结果时返回.
     */
    @Schema(name = "finalResult", description = "最终结果集")
    private ExecSqlResultDto finalResult;

    /**
     * 任务级错误信息.
     */
    @Schema(name = "errorMsg", description = "任务级错误信息")
    private String errorMsg;

    /**
     * 提交时间, 毫秒.
     */
    @Schema(name = "submittedAt", description = "提交时间(毫秒)")
    private Long submittedAt;

    /**
     * 开始时间, 毫秒.
     */
    @Schema(name = "startedAt", description = "开始时间(毫秒)")
    private Long startedAt;

    /**
     * 完成时间, 毫秒.
     */
    @Schema(name = "finishedAt", description = "完成时间(毫秒)")
    private Long finishedAt;
}
