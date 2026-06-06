package com.datafusion.manager.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调度-任务实例日志响应Dto.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Data
@Schema(name = "TaskInstanceLogDto", description = "任务实例日志响应Dto")
public class TaskInstanceLogDto {

    /**
     * 日志类型.
     */
    @Schema(name = "logType", description = "日志类型")
    private String logType;

    /**
     * 日志路径.
     */
    @Schema(name = "path", description = "日志路径")
    private String path;

    /**
     * 日志内容.
     */
    @Schema(name = "content", description = "日志内容")
    private String content;

    /**
     * 下一次读取偏移.
     */
    @Schema(name = "nextOffset", description = "下一次读取偏移")
    private Long nextOffset;

    /**
     * 是否还有更多内容.
     */
    @Schema(name = "hasMore", description = "是否还有更多内容")
    private Boolean hasMore;
}
