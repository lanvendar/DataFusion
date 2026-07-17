package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.Variable;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
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

    /**
     * 创建任务执行状态深副本.
     *
     * @return 任务执行状态深副本
     */
    public WorkerTaskExecutionState copy() {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(taskInstanceId)
                .workerId(workerId)
                .appId(appId)
                .workDirPath(workDirPath)
                .status(status)
                .revision(revision)
                .exitCode(exitCode)
                .updateTime(updateTime)
                .result(result == null ? null : result.deepCopy())
                .outputVars(copyOutputVars())
                .build();
    }

    private Map<String, Variable> copyOutputVars() {
        if (outputVars == null) {
            return null;
        }
        Map<String, Variable> copiedVars = new LinkedHashMap<>();
        outputVars.forEach((name, variable) -> copiedVars.put(name, copyVariable(variable)));
        return copiedVars;
    }

    private Variable copyVariable(Variable source) {
        if (source == null) {
            return null;
        }
        Variable target = new Variable();
        target.setName(source.getName());
        target.setType(source.getType());
        target.setValue(source.getValue());
        return target;
    }
}
