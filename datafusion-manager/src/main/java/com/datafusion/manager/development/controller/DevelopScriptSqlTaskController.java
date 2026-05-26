package com.datafusion.manager.development.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskQueryDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskSaveDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskUpdateDto;
import com.datafusion.manager.development.service.DevelopScriptSqlTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 数据开发-SQL脚本任务管理.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@RestController
@RequestMapping("/api/development/script-sql-task")
@Tag(name = "【数据开发-SQL脚本任务】")
@RequiredArgsConstructor
public class DevelopScriptSqlTaskController {

    /**
     * SQL脚本任务服务.
     */
    private final DevelopScriptSqlTaskService developScriptSqlTaskService;

    /**
     * 分页查询SQL脚本任务.
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询SQL脚本任务")
    public Result<PageResponse<DevelopScriptSqlTaskDto>> page(
            @RequestBody PageQuery<DevelopScriptSqlTaskQueryDto> query) {
        return Result.success(developScriptSqlTaskService.pageTask(query));
    }

    /**
     * 查询SQL脚本任务列表.
     *
     * @param query 查询条件
     * @return 列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询SQL脚本任务列表")
    public Result<List<DevelopScriptSqlTaskDto>> list(
            @RequestBody DevelopScriptSqlTaskQueryDto query) {
        return Result.success(developScriptSqlTaskService.listTask(query));
    }

    /**
     * 查询SQL脚本任务详情.
     *
     * @param id 任务id
     * @return 详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "查询SQL脚本任务详情")
    public Result<DevelopScriptSqlTaskDto> detail(@PathVariable UUID id) {
        return Result.success(developScriptSqlTaskService.getTaskById(id));
    }

    /**
     * 新增SQL脚本任务.
     *
     * @param dto 入参
     * @return 新任务主键
     */
    @PostMapping("/add")
    @Operation(summary = "新增SQL脚本任务")
    public Result<UUID> add(@RequestBody @Validated DevelopScriptSqlTaskSaveDto dto) {
        return Result.success(developScriptSqlTaskService.addTask(dto));
    }

    /**
     * 修改SQL脚本任务.
     *
     * @param dto 入参
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改SQL脚本任务")
    public Result<Boolean> update(@RequestBody @Validated DevelopScriptSqlTaskUpdateDto dto) {
        return Result.success(developScriptSqlTaskService.updateTask(dto));
    }

    /**
     * 软删除SQL脚本任务.
     *
     * @param id 任务id
     * @return 是否成功
     */
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除SQL脚本任务（软删）")
    public Result<Boolean> delete(@PathVariable UUID id) {
        return Result.success(developScriptSqlTaskService.softDeleteTask(id));
    }

    /**
     * 发布SQL脚本任务.
     *
     * @param id 任务id
     * @return 是否成功
     */
    @PostMapping("/publish/{id}")
    @Operation(summary = "发布SQL脚本任务")
    public Result<Boolean> publish(@PathVariable UUID id) {
        return Result.success(developScriptSqlTaskService.publishTask(id));
    }
}
