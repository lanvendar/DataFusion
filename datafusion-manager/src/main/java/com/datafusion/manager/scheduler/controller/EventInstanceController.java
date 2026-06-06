package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.EventInstanceDto;
import com.datafusion.manager.scheduler.dto.EventInstanceQueryDto;
import com.datafusion.manager.scheduler.service.EventInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 调度-事件实例.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/event/instance")
@Tag(name = "【调度事件实例】")
@RequiredArgsConstructor
public class EventInstanceController {

    /**
     * 事件实例Service.
     */
    private final EventInstanceService eventInstanceService;

    /**
     * 分页查询事件实例.
     *
     * @param query 查询条件
     * @return 事件实例分页
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询事件实例")
    public Result<PageResponse<EventInstanceDto>> page(@RequestBody PageQuery<EventInstanceQueryDto> query) {
        return Result.success(eventInstanceService.pageEventInstance(query));
    }

    /**
     * 查询事件实例详情.
     *
     * @param id 事件实例ID
     * @return 事件实例详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询事件实例详情")
    public Result<EventInstanceDto> getById(@PathVariable UUID id) {
        return Result.success(eventInstanceService.getEventInstanceById(id));
    }
}
