package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flink Kubernetes runtime reference.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlinkKubernetesRuntimeRef {

    /**
     * Kubernetes namespace.
     */
    private String namespace;

    /**
     * FlinkDeployment name.
     */
    private String deploymentName;

    /**
     * Secret name.
     */
    private String secretName;

    /**
     * Pod label selector.
     */
    private String podLabelSelector;

    /**
     * External log storage URI.
     */
    private String logStorageUri;

    /**
     * Flink web UI URI.
     */
    private String flinkWebUiUri;

    /**
     * Whether collect logs on finish.
     */
    private boolean collectLogsOnFinish;

    /**
     * Whether delete deployment on finish.
     */
    private boolean deleteDeploymentOnFinish;

    /**
     * Whether delete secret on finish.
     */
    private boolean deleteSecretOnFinish;
}
