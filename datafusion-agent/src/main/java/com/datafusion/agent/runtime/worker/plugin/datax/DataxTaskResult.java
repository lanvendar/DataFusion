package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesRuntimeRef;
import com.datafusion.scheduler.enums.StatusEnum;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * DataX task runner result.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Data
@Builder
public class DataxTaskResult {

    /**
     * Task status.
     */
    private StatusEnum status;

    /**
     * Terminal application ID.
     */
    private String appId;

    /**
     * Task runtime work directory path.
     */
    private String workDirPath;

    /**
     * Result message.
     */
    private JsonNode result;

    /**
     * Kubernetes runtime reference.
     */
    private DataxKubernetesRuntimeRef kubernetesRuntimeRef;
}
