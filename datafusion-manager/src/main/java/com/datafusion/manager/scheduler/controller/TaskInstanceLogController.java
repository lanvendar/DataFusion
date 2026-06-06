package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogQueryDto;
import com.datafusion.manager.scheduler.service.TaskInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调度-任务实例日志.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/task/instance/log")
@Tag(name = "【调度任务实例日志】")
@RequiredArgsConstructor
public class TaskInstanceLogController {

    /**
     * 任务实例Service.
     */
    private final TaskInstanceService taskInstanceService;

    /**
     * 读取任务实例日志.
     *
     * @param query 查询条件
     * @return 任务实例日志
     */
    @PostMapping("/content")
    @Operation(summary = "读取任务实例日志")
    public Result<TaskInstanceLogDto> content(@RequestBody TaskInstanceLogQueryDto query) {
        return Result.success(taskInstanceService.readTaskInstanceLog(query));
    }
}
