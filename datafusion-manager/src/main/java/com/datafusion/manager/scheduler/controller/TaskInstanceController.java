package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.FlowInstanceTaskQueryDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceDto;
import com.datafusion.manager.scheduler.service.TaskInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务实例.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/task/instance")
@Tag(name = "【调度任务实例】")
@RequiredArgsConstructor
public class TaskInstanceController {

    /**
     * 任务实例Service.
     */
    private final TaskInstanceService taskInstanceService;

    /**
     * 分页查询任务实例.
     *
     * @param query 查询条件
     * @return 任务实例分页
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询任务实例")
    public Result<PageResponse<TaskInstanceDto>> page(@RequestBody PageQuery<SchedulerInstanceQueryDto> query) {
        return Result.success(taskInstanceService.pageTaskInstance(query));
    }

    /**
     * 查询流程实例下的任务实例.
     *
     * @param query 查询条件
     * @return 任务实例列表
     */
    @PostMapping("/listByFlowInstance")
    @Operation(summary = "查询流程实例下的任务实例")
    public Result<List<TaskInstanceDto>> listByFlowInstance(@RequestBody FlowInstanceTaskQueryDto query) {
        return Result.success(taskInstanceService.listByFlowInstance(query));
    }

    /**
     * 查询任务实例详情.
     *
     * @param id 任务实例ID
     * @return 任务实例详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询任务实例详情")
    public Result<TaskInstanceDto> getById(@PathVariable UUID id) {
        return Result.success(taskInstanceService.getTaskInstanceById(id));
    }
}
