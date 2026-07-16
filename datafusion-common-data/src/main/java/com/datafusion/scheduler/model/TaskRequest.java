package com.datafusion.scheduler.model;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 任务动作请求发送报文.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/5
 * @since 2024/12/5
 */
@Data
public class TaskRequest {

    /**
     * 流程实例ID.
     */
    private String flowInstanceId;

    /**
     * 任务实例ID.
     */
    private String taskInstanceId;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 任务状态.
     */
    private StatusEnum taskState;

    /**
     * 渲染后的任务执行数据.
     */
    private JsonNode taskData;

    /**
     * 组件类型.
     */
    private String pluginType;

    /**
     * 运行模式.
     */
    private String runMode;

    /**
     * 组件参数.
     */
    private JsonNode pluginParam;

    /**
     * 提交模式.
     */
    private SubmitModeEnum submitMode = SubmitModeEnum.SYNC;

    /**
     * worker 执行上下文.
     * manager 发起新任务时通常为空；agent 发起状态同步时用于传递 worker、外部任务和运行目录信息.
     */
    private WorkerResult workerResult;
}
