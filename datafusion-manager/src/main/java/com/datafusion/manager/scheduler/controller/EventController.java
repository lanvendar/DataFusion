package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.EventInfoDto;
import com.datafusion.manager.scheduler.dto.EventInfoQueryDto;
import com.datafusion.manager.scheduler.dto.EventInfoSaveDto;
import com.datafusion.manager.scheduler.dto.EventInfoUpdateDto;
import com.datafusion.manager.scheduler.service.EventInfoService;
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
 * 调度-事件配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/event")
@Tag(name = "【调度事件配置】")
@RequiredArgsConstructor
public class EventController {

    /**
     * 事件信息Service.
     */
    private final EventInfoService eventInfoService;

    /**
     * 分页查询事件.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询事件")
    public Result<PageResponse<EventInfoDto>> page(@RequestBody PageQuery<EventInfoQueryDto> query) {
        return Result.success(eventInfoService.pageEventInfo(query));
    }

    /**
     * 查询事件列表.
     *
     * @param query 查询条件
     * @return 事件列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询事件列表")
    public Result<List<EventInfoDto>> list(@RequestBody EventInfoQueryDto query) {
        return Result.success(eventInfoService.listEventInfo(query));
    }

    /**
     * 新增事件.
     *
     * @param dto 新增参数
     * @return 事件ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增事件")
    public Result<UUID> add(@RequestBody @Validated EventInfoSaveDto dto) {
        return Result.success(eventInfoService.addEventInfo(dto));
    }

    /**
     * 修改事件.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改事件")
    public Result<Boolean> update(@RequestBody @Validated EventInfoUpdateDto dto) {
        return Result.success(eventInfoService.updateEventInfo(dto));
    }

    /**
     * 根据ID查询事件详情.
     *
     * @param id 事件ID
     * @return 事件详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "根据ID查询事件详情")
    public Result<EventInfoDto> getById(@PathVariable UUID id) {
        return Result.success(eventInfoService.getEventInfoById(id));
    }

    /**
     * 删除事件.
     *
     * @param id 事件ID
     * @return 是否成功
     */
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除事件")
    public Result<Boolean> delete(@PathVariable UUID id) {
        return Result.success(eventInfoService.deleteEventInfo(id));
    }
}
