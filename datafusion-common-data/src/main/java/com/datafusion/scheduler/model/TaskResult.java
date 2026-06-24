package com.datafusion.scheduler.model;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /**
     * worker 端提交模式，默认为同步提交.
     */
    @Builder.Default
    private SubmitModeEnum submitMode = SubmitModeEnum.SYNC;
    //endregion
    //region 返回字段

    /**
     * 执行节点返回结果.
     */
    private WorkerResult workerResult;
    //endregion
}
