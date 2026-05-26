package com.datafusion.manager.ingestion.controller;

import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableDto;
import com.datafusion.manager.ingestion.dto.ExecuteCreateTableResultDto;
import com.datafusion.manager.ingestion.service.IngestionSqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集成.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/17
 * @since 2026/4/17
 */
@RestController
@RequestMapping("/api/ingestion")
@Tag(name = "【数据集成】")
@RequiredArgsConstructor
public class IngestionSqlController {

    /**
     * 数据集成SQL执行服务.
     */
    private final IngestionSqlService ingestionSqlService;

    /**
     * 按数据源执行建表语句.
     *
     * @param executeCreateTableDto 建表执行请求
     * @return 建表执行结果
     */
    @Operation(summary = "按数据源执行建表语句")
    @PostMapping("/executeCreateTable")
    public Result<ExecuteCreateTableResultDto> executeCreateTable(
            @RequestBody @Validated ExecuteCreateTableDto executeCreateTableDto) {
        return Result.success(ingestionSqlService.executeCreateTable(executeCreateTableDto));
    }
}
