package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.config.FileBrowserProperties;
import com.datafusion.manager.scheduler.dto.TaskInstanceDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogQueryDto;
import com.datafusion.manager.scheduler.service.TaskInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

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
     * File Browser 配置.
     */
    private final FileBrowserProperties fileBrowserProperties;

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

    /**
     * 跳转到任务运行目录.
     *
     * @param taskInstanceId 任务实例ID
     * @return 跳转响应
     */
    @GetMapping("/filebrowser/{taskInstanceId}")
    @Operation(summary = "跳转到任务运行目录")
    public ResponseEntity<Void> redirectFileBrowser(@PathVariable UUID taskInstanceId) {
        URI uri = buildFileBrowserUri(taskInstanceId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toString())
                .build();
    }

    private URI buildFileBrowserUri(UUID taskInstanceId) {
        if (!fileBrowserProperties.isEnabled()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "File Browser未启用");
        }
        if (StringUtils.isAnyBlank(fileBrowserProperties.getBaseUrl(), fileBrowserProperties.getRootPath())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "File Browser配置不完整");
        }
        TaskInstanceDto taskInstance = taskInstanceService.getTaskInstanceById(taskInstanceId);
        if (StringUtils.isBlank(taskInstance.getWorkDirPath())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务运行目录不存在");
        }

        Path rootPath = Path.of(fileBrowserProperties.getRootPath()).normalize();
        Path workDirPath = Path.of(taskInstance.getWorkDirPath()).normalize();
        if (!workDirPath.startsWith(rootPath)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务运行目录不在允许访问范围内");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(fileBrowserProperties.getBaseUrl());
        Path relativePath = rootPath.relativize(workDirPath);
        for (Path path : relativePath) {
            builder.pathSegment(path.toString());
        }
        return builder.build()
                .encode()
                .toUri();
    }
}
