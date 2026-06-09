package com.datafusion.scheduler.model;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 任务结果参数.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/7/26
 * @since 2022/7/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {
    //region 请求和返回公共字段

    /**
     * task instance id.
     */
    private String taskInstanceId;

    /**
     * flow instance id.
     */
    private String flowInstanceId;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 任务执行状态.
     */
    private StatusEnum taskState;
    //endregion
    //region 返回字段

    /**
     * 输出变量列表.
     */
    private Map<String, Variable> outputVars;

    /**
     * 获取worker id.
     */
    private String workerId;

    /**
     * 返回执行的application id.
     */
    private String appId;

    /**
     * 任务日志文件路径.
     */
    private String logPath;

    /**
     * worker 端提交模式，默认为同步提交.
     */
    @Builder.Default
    private SubmitModeEnum submitMode = SubmitModeEnum.SYNC;

    /**
     * 执行节点返回结果.
     */
    private JsonNode result;
    //endregion
}
