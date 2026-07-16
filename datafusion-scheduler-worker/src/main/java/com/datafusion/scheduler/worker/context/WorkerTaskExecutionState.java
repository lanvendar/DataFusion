package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.Variable;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
     * 工作节点 ID.
     */
    private String workerId;

    /**
     * 终端任务 ID.
     */
    private String appId;

    /**
     * 任务运行目录路径.
     */
    private String workDirPath;

    /**
     * 执行状态.
     */
    private StatusEnum status;

    /**
     * 运行态版本号.
     */
    private long revision;

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

    /**
     * 输出变量列表.
     */
    private Map<String, Variable> outputVars;
}
