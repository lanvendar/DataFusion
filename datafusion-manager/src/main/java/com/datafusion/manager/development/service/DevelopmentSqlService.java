package com.datafusion.manager.development.service;

import com.datafusion.manager.development.dto.ExecSqlResultDto;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;

/**
 * 开发侧SQL执行服务.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
public interface DevelopmentSqlService {

    /**
     * 执行sql脚本.
     *
     * @param executeCreateTableDto sql执行脚本实体
     * @return sql执行结果
     */
    ExecSqlResultDto execSql(ExecuteCreateTableDto executeCreateTableDto);
}
