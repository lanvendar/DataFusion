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
     * 第三方插件运行唯一主键 appId.
     */
    private String appId;

    /**
     * 组件类型.
     */
    private String pluginType;

    /**
     * 组件参数.
     */
    private JsonNode pluginParam;

    /**
     * 提交模式.
     */
    private SubmitModeEnum submitMode = SubmitModeEnum.SYNC;
}
