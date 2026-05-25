package com.datafusion.manager.development.service;

import com.datafusion.manager.development.dto.ExecSqlJobStatusDto;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;

import java.util.UUID;

/**
 * 开发侧SQL异步执行服务.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
public interface AsyncSqlExecService {

    /**
     * 提交一个异步SQL执行任务.
     *
     * @param dto 提交参数
     * @return 任务ID
     */
    UUID submit(ExecuteCreateTableDto dto);

    /**
     * 查询任务状态及增量日志.
     *
     * @param jobId     任务ID
     * @param logOffset 日志偏移量, 从 0 开始
     * @return 任务状态
     */
    ExecSqlJobStatusDto getStatus(UUID jobId, int logOffset);

    /**
     * 取消任务.
     *
     * @param jobId 任务ID
     * @return 任务最新状态
     */
    ExecSqlJobStatusDto cancel(UUID jobId);
}
