package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Worker 任务执行提交快照.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTaskExecutionSnap {

    /**
     * 流程实例 ID.
     */
    private String flowInstanceId;

    /**
     * 任务实例 ID.
     */
    private String taskInstanceId;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 插件类型.
     */
    private String pluginType;

    /**
     * 运行模式.
     */
    private String runMode;

    /**
     * 工作节点 ID.
     */
    private String workerId;

    /**
     * 渲染后的任务执行数据.
     */
    private JsonNode taskData;

    /**
     * 插件参数.
     */
    private JsonNode pluginParam;

    /**
     * 提交模式.
     */
    private SubmitModeEnum submitMode;

    /**
     * 创建任务提交快照副本.
     *
     * @return 任务提交快照副本
     */
    public WorkerTaskExecutionSnap copy() {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(flowInstanceId)
                .taskInstanceId(taskInstanceId)
                .taskName(taskName)
                .pluginType(pluginType)
                .runMode(runMode)
                .workerId(workerId)
                .taskData(taskData == null ? null : taskData.deepCopy())
                .pluginParam(pluginParam == null ? null : pluginParam.deepCopy())
                .submitMode(submitMode)
                .build();
    }
}
