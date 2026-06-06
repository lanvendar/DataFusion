package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.FlowInstanceDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.service.FlowInstanceService;
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
 * 调度-流程实例.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/flow/instance")
@Tag(name = "【调度流程实例】")
@RequiredArgsConstructor
public class FlowInstanceController {

    /**
     * 流程实例Service.
     */
    private final FlowInstanceService flowInstanceService;

    /**
     * 分页查询流程实例.
     *
     * @param query 查询条件
     * @return 流程实例分页
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询流程实例")
    public Result<PageResponse<FlowInstanceDto>> page(@RequestBody PageQuery<SchedulerInstanceQueryDto> query) {
        return Result.success(flowInstanceService.pageFlowInstance(query));
    }

    /**
     * 查询流程实例详情.
     *
     * @param id 流程实例ID
     * @return 流程实例详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询流程实例详情")
    public Result<FlowInstanceDto> getById(@PathVariable UUID id) {
        return Result.success(flowInstanceService.getFlowInstanceById(id));
    }
}
