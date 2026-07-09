package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * Spark runner 返回结果.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Data
@Builder
public class SparkTaskResult {

    /**
     * 任务状态.
     */
    private StatusEnum status;

    /**
     * 应用 ID.
     */
    private String appId;

    /**
     * 工作目录.
     */
    private String workDirPath;

    /**
     * 结果.
     */
    private JsonNode result;

}
