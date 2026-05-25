package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.common.web.dto.response.Result;
import com.datafusion.manager.scheduler.dto.TriggerInfoDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoUpdateDto;
import com.datafusion.manager.scheduler.service.TriggerInfoService;
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
 * 调度-触发器配置.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/26
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/trigger")
@Tag(name = "【调度器配置】")
@RequiredArgsConstructor
public class TriggerController {

    /**
     * 触发器信息Service.
     */
    private final TriggerInfoService triggerInfoService;

    /**
     * 分页查询触发器.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询触发器")
    public Result<PageResponse<TriggerInfoDto>> page(@RequestBody PageQuery<TriggerInfoQueryDto> query) {
        return Result.success(triggerInfoService.pageTriggerInfo(query));
    }

    /**
     * 查询触发器列表.
     *
     * @param query 查询条件
     * @return 触发器列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询触发器列表")
    public Result<List<TriggerInfoDto>> list(@RequestBody TriggerInfoQueryDto query) {
        return Result.success(triggerInfoService.listTriggerInfo(query));
    }

    /**
     * 新增触发器.
     *
     * @param dto 新增参数
     * @return 触发器ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增触发器")
    public Result<UUID> add(@RequestBody @Validated TriggerInfoSaveDto dto) {
        return Result.success(triggerInfoService.addTriggerInfo(dto));
    }

    /**
     * 修改触发器.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改触发器")
    public Result<Boolean> update(@RequestBody @Validated TriggerInfoUpdateDto dto) {
        return Result.success(triggerInfoService.updateTriggerInfo(dto));
    }

    /**
     * 根据ID查询触发器.
     *
     * @param id 触发器ID
     * @return 触发器详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询触发器")
    public Result<TriggerInfoDto> getById(@PathVariable("id") UUID id) {
        return Result.success(triggerInfoService.getTriggerInfoById(id));
    }

    /**
     * 删除触发器.
     *
     * @param id 触发器ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除触发器")
    public Result<Boolean> delete(@PathVariable("id") UUID id) {
        return Result.success(triggerInfoService.deleteTriggerInfo(id));
    }
}
