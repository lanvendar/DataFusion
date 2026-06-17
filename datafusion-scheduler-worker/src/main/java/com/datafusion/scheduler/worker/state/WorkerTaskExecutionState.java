package com.datafusion.scheduler.worker.state;

import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Worker 任务执行运行态.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTaskExecutionState {

    /**
     * 任务实例ID.
     */
    private String taskInstanceId;

    /**
     * 终端任务 ID.
     */
    private String appId;

    /**
     * 任务日志文件路径.
     */
    private String logPath;

    /**
     * 执行状态.
     */
    private StatusEnum status;

    /**
     * 本地进程退出码.
     */
    private Integer exitCode;

    /**
     * 状态更新时间.
     */
    private Long updateTime;

    /**
     * 执行结果说明.
     */
    private JsonNode result;
}
