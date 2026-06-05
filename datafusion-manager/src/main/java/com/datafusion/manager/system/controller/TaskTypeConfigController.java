package com.datafusion.manager.system.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.system.dto.TaskTypeConfigDto;
import com.datafusion.manager.system.dto.TaskTypeConfigQueryDto;
import com.datafusion.manager.system.dto.TaskTypeConfigSaveDto;
import com.datafusion.manager.system.dto.TaskTypeConfigUpdateDto;
import com.datafusion.manager.system.service.TaskTypeConfigService;
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
 * 系统-任务类型配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/system/task-type")
@Tag(name = "【系统任务类型配置】")
@RequiredArgsConstructor
public class TaskTypeConfigController {

    /**
     * 任务类型配置Service.
     */
    private final TaskTypeConfigService taskTypeConfigService;

    /**
     * 分页查询任务类型配置.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询任务类型配置")
    public Result<PageResponse<TaskTypeConfigDto>> page(@RequestBody PageQuery<TaskTypeConfigQueryDto> query) {
        return Result.success(taskTypeConfigService.pageTaskTypeConfig(query));
    }

    /**
     * 查询任务类型配置列表.
     *
     * @param query 查询条件
     * @return 任务类型配置列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询任务类型配置列表")
    public Result<List<TaskTypeConfigDto>> list(@RequestBody TaskTypeConfigQueryDto query) {
        return Result.success(taskTypeConfigService.listTaskTypeConfig(query));
    }

    /**
     * 新增任务类型配置.
     *
     * @param dto 新增参数
     * @return 任务类型配置ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增任务类型配置")
    public Result<UUID> add(@RequestBody @Validated TaskTypeConfigSaveDto dto) {
        return Result.success(taskTypeConfigService.addTaskTypeConfig(dto));
    }

    /**
     * 修改任务类型配置.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改任务类型配置")
    public Result<Boolean> update(@RequestBody @Validated TaskTypeConfigUpdateDto dto) {
        return Result.success(taskTypeConfigService.updateTaskTypeConfig(dto));
    }

    /**
     * 根据ID查询任务类型配置.
     *
     * @param id 任务类型配置ID
     * @return 任务类型配置详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询任务类型配置")
    public Result<TaskTypeConfigDto> getById(@PathVariable("id") UUID id) {
        return Result.success(taskTypeConfigService.getTaskTypeConfigById(id));
    }

    /**
     * 删除任务类型配置.
     *
     * @param id 任务类型配置ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务类型配置")
    public Result<Boolean> delete(@PathVariable("id") UUID id) {
        return Result.success(taskTypeConfigService.deleteTaskTypeConfig(id));
    }
}
