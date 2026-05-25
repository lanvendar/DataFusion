package com.datafusion.manager.ingestion.service;

import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableResultDto;

/**
 * 数据集成SQL执行服务.
 *
 * @author codex
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
public interface IngestionSqlService {

    /**
     * 通过数据源ID执行建表语句.
     *
     * @param executeCreateTableDto 建表执行请求
     * @return 建表执行结果
     */
    ExecuteCreateTableResultDto executeCreateTable(ExecuteCreateTableDto executeCreateTableDto);
}
