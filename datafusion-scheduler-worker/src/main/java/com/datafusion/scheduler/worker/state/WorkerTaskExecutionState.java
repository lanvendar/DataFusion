package com.datafusion.scheduler.worker.state;

import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Worker 任务执行状态.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTaskExecutionState {

    /**
     * 流程实例ID.
     */
    private String flowInstanceId;

    /**
     * 任务实例ID.
     */
    private String taskInstanceId;

    /**
     * 插件类型.
     */
    private String pluginType;

    /**
     * 终端运行模式.
     */
    private String runMode;

    /**
     * 终端任务 ID.
     */
    private String appId;

    /**
     * 任务日志文件路径.
     */
    private String logPath;

    /**
     * 工作节点ID.
     */
    private String workId;

    /**
     * 执行状态.
     */
    private StatusEnum status;

    /**
     * 渲染后的任务执行数据.
     */
    private JsonNode taskData;

    /**
     * 插件参数.
     */
    private JsonNode pluginParam;

    /**
     * 本地进程退出码.
     */
    private Integer exitCode;

    /**
     * 执行结果说明.
     */
    private JsonNode result;
}
