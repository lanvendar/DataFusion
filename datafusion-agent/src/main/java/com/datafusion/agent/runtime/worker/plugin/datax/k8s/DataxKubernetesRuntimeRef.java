package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DataX Kubernetes runtime reference.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataxKubernetesRuntimeRef {

    /**
     * Kubernetes namespace.
     */
    private String namespace;

    /**
     * Kubernetes Job name.
     */
    private String jobName;

    /**
     * Kubernetes Secret name.
     */
    private String secretName;

    /**
     * Pod label selector.
     */
    private String podLabelSelector;

    /**
     * Main container name.
     */
    private String containerName;

    /**
     * External log storage URI.
     */
    private String logStorageUri;

    /**
     * Whether collect logs on finish.
     */
    private boolean collectLogsOnFinish;

    /**
     * Whether delete Job on finish.
     */
    private boolean deleteJobOnFinish;
}
