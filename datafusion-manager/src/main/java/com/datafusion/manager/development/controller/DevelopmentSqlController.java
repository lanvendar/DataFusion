package com.datafusion.manager.development.controller;

import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.development.dto.ExecSqlJobIdDto;
import com.datafusion.manager.development.dto.ExecSqlJobStatusDto;
import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.development.service.AsyncSqlExecService;
import com.datafusion.manager.development.service.DevelopmentSqlService;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 数据开发.
 *
 * @author codex
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@RestController
@RequestMapping("/api/development")
@Tag(name = "【数据开发】")
@RequiredArgsConstructor
public class DevelopmentSqlController {

    /**
     * 开发侧SQL执行服务.
     */
    private final DevelopmentSqlService developmentSqlService;

    /**
     * 开发侧SQL异步执行服务.
     */
    private final AsyncSqlExecService asyncSqlExecService;

    /**
     * 同步执行SQL脚本.
     *
     * @param executeCreateTableDto sql执行脚本实体
     * @return sql执行结果
     */
    @Operation(summary = "执行各种类型sql脚本")
    @PostMapping("/execSql")
    public Result<ExecSqlResultDto> execSql(@RequestBody @Validated ExecuteCreateTableDto executeCreateTableDto) {
        return Result.success(developmentSqlService.execSql(executeCreateTableDto));
    }

    /**
     * 提交一个异步SQL执行任务.
     *
     * @param executeCreateTableDto sql执行脚本实体
     * @return 任务ID
     */
    @Operation(summary = "异步提交SQL执行任务")
    @PostMapping("/execSql/submit")
    public Result<ExecSqlJobIdDto> submit(@RequestBody @Validated ExecuteCreateTableDto executeCreateTableDto) {
        UUID jobId = asyncSqlExecService.submit(executeCreateTableDto);
        ExecSqlJobIdDto dto = new ExecSqlJobIdDto();
        dto.setJobId(jobId);
        return Result.success(dto);
    }

    /**
     * 查询异步SQL执行任务状态及增量日志.
     *
     * @param jobId     任务ID
     * @param logOffset 日志偏移量, 默认 0
     * @return 任务状态
     */
    @Operation(summary = "查询SQL执行任务状态")
    @GetMapping("/execSql/status")
    public Result<ExecSqlJobStatusDto> status(@RequestParam("jobId") UUID jobId,
                                              @RequestParam(value = "logOffset", required = false, defaultValue = "0") Integer logOffset) {
        return Result.success(asyncSqlExecService.getStatus(jobId, logOffset == null ? 0 : logOffset));
    }

    /**
     * 取消异步SQL执行任务.
     *
     * @param jobIdDto 任务ID
     * @return 任务最新状态
     */
    @Operation(summary = "取消SQL执行任务")
    @PostMapping("/execSql/cancel")
    public Result<ExecSqlJobStatusDto> cancel(@RequestBody @Validated ExecSqlJobIdDto jobIdDto) {
        return Result.success(asyncSqlExecService.cancel(jobIdDto.getJobId()));
    }
}
