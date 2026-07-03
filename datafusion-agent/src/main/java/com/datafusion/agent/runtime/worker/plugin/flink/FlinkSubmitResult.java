package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesRuntimeRef;
import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * Flink submit result.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Data
@Builder
public class FlinkSubmitResult {

    /**
     * Submit status.
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

    /**
     * Kubernetes runtime reference.
     */
    private FlinkKubernetesRuntimeRef kubernetesRuntimeRef;
}
