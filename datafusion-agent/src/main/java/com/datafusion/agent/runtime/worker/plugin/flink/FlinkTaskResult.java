package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * Flink task result.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Data
@Builder
public class FlinkTaskResult {

    /**
     * Task status.
     */
    private StatusEnum status;

    /**
     * External application ID.
     */
    private String appId;

    /**
     * Work directory path.
     */
    private String workDirPath;

    /**
     * Result JSON.
     */
    private JsonNode result;

}
