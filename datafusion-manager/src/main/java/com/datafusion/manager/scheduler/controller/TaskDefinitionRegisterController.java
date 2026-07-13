package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.TaskDefinitionMarkUnsyncedResultDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterResultDto;
import com.datafusion.manager.scheduler.model.BusinessSourceRoute;
import com.datafusion.manager.scheduler.service.TaskDefinitionRegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务定义统一登记.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/task-definition")
@Tag(name = "【任务定义统一登记】")
@RequiredArgsConstructor
public class TaskDefinitionRegisterController {

    /**
     * 任务定义统一登记Service.
     */
    private final TaskDefinitionRegisterService taskDefinitionRegisterService;

    /**
     * 登记任务定义.
     *
     * @param dto 登记参数
     * @return 登记结果
     */
    @PostMapping("/register")
    @Operation(summary = "登记任务定义")
    public Result<TaskDefinitionRegisterResultDto> register(@RequestBody @Validated TaskDefinitionRegisterDto dto) {
        return Result.success(taskDefinitionRegisterService.register(dto));
    }

    /**
     * 标记任务定义未同步.
     *
     * @param sourceRoute 业务来源定位信息
     * @return 标记结果
     */
    @PostMapping("/mark-unsynced")
    @Operation(summary = "标记任务定义未同步")
    public Result<TaskDefinitionMarkUnsyncedResultDto> markUnsynced(
            @RequestBody @Validated BusinessSourceRoute sourceRoute) {
        return Result.success(taskDefinitionRegisterService.markUnsynced(sourceRoute));
    }
}
