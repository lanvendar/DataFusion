package com.datafusion.scheduler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Worker 执行结果.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/23
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerResult {

    /**
     * 输出变量列表.
     */
    private Map<String, Variable> outputVars;

    /**
     * worker ID.
     */
    private String workerId;

    /**
     * 外部终端任务 ID.
     */
    private String appId;

    /**
     * 任务运行目录路径.
     */
    private String workDirPath;

    /**
     * 执行说明.
     */
    private String message;

    /**
     * 插件日志入口.
     */
    private String pluginLogUri;
}
