package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.common.web.dto.response.Result;
import com.datafusion.manager.scheduler.dto.TaskInfoDto;
import com.datafusion.manager.scheduler.dto.TaskInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TaskInfoUpdateDto;
import com.datafusion.manager.scheduler.service.TaskInfoService;
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
 * 调度-任务配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/task")
@Tag(name = "【调度任务配置】")
@RequiredArgsConstructor
public class TaskController {

    /**
     * 任务信息Service.
     */
    private final TaskInfoService taskInfoService;

    /**
     * 分页查询任务.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询任务")
    public Result<PageResponse<TaskInfoDto>> page(@RequestBody PageQuery<TaskInfoQueryDto> query) {
        return Result.success(taskInfoService.pageTaskInfo(query));
    }

    /**
     * 查询任务列表.
     *
     * @param query 查询条件
     * @return 任务列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询任务列表")
    public Result<List<TaskInfoDto>> list(@RequestBody TaskInfoQueryDto query) {
        return Result.success(taskInfoService.listTaskInfo(query));
    }

    /**
     * 新增任务.
     *
     * @param dto 新增参数
     * @return 任务ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增任务")
    public Result<UUID> add(@RequestBody @Validated TaskInfoSaveDto dto) {
        return Result.success(taskInfoService.addTaskInfo(dto));
    }

    /**
     * 修改任务.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改任务")
    public Result<Boolean> update(@RequestBody @Validated TaskInfoUpdateDto dto) {
        return Result.success(taskInfoService.updateTaskInfo(dto));
    }

    /**
     * 根据ID查询任务.
     *
     * @param id 任务ID
     * @return 任务详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "根据ID查询任务")
    public Result<TaskInfoDto> getById(@PathVariable UUID id) {
        return Result.success(taskInfoService.getTaskInfoById(id));
    }

    /**
     * 删除任务.
     *
     * @param id 任务ID
     * @return 是否成功
     */
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除任务")
    public Result<Boolean> delete(@PathVariable UUID id) {
        return Result.success(taskInfoService.deleteTaskInfo(id));
    }
}
