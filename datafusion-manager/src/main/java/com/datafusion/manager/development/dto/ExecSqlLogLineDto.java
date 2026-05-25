package com.datafusion.manager.development.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开发侧SQL执行日志行.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/29
 * @since 2026/4/29
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ExecSqlLogLineDto", description = "开发侧SQL执行日志行")
public class ExecSqlLogLineDto {

    /**
     * 日志时间戳, 毫秒.
     */
    @Schema(name = "ts", description = "日志时间戳(毫秒)")
    private Long ts;

    /**
     * 日志级别, INFO/WARN/ERROR.
     */
    @Schema(name = "level", description = "日志级别")
    private String level;

    /**
     * 日志正文.
     */
    @Schema(name = "message", description = "日志正文")
    private String message;
}
