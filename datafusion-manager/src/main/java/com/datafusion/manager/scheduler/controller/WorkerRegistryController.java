package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.WorkerRegistryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryQueryDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistrySaveDto;
import com.datafusion.manager.scheduler.dto.WorkerRegistryUpdateDto;
import com.datafusion.manager.scheduler.service.WorkerRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 调度-worker 管理.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/worker")
@Tag(name = "【调度worker管理】")
@RequiredArgsConstructor
public class WorkerRegistryController {

    /**
     * worker 注册Service.
     */
    private final WorkerRegistryService workerRegistryService;

    /**
     * 分页查询 worker.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询worker")
    public Result<PageResponse<WorkerRegistryDto>> page(@RequestBody PageQuery<WorkerRegistryQueryDto> query) {
        return Result.success(workerRegistryService.pageWorkerRegistry(query));
    }

    /**
     * 查询 worker 列表.
     *
     * @param query 查询条件
     * @return worker 列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询worker列表")
    public Result<List<WorkerRegistryDto>> list(@RequestBody WorkerRegistryQueryDto query) {
        return Result.success(workerRegistryService.listWorkerRegistry(query));
    }

    /**
     * 新增 worker.
     *
     * @param dto 新增参数
     * @return worker 注册记录ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增worker")
    public Result<UUID> add(@RequestBody @Validated WorkerRegistrySaveDto dto) {
        return Result.success(workerRegistryService.addWorkerRegistry(dto));
    }

    /**
     * 修改 worker.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改worker")
    public Result<Boolean> update(@RequestBody @Validated WorkerRegistryUpdateDto dto) {
        return Result.success(workerRegistryService.updateWorkerRegistry(dto));
    }

    /**
     * 根据ID查询 worker.
     *
     * @param id worker 注册记录ID
     * @return worker 详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询worker")
    public Result<WorkerRegistryDto> getById(@PathVariable("id") UUID id) {
        return Result.success(workerRegistryService.getWorkerRegistryById(id));
    }

    /**
     * 删除 worker.
     *
     * @param id worker 注册记录ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除worker")
    public Result<Boolean> delete(@PathVariable("id") UUID id) {
        return Result.success(workerRegistryService.deleteWorkerRegistry(id));
    }
}
