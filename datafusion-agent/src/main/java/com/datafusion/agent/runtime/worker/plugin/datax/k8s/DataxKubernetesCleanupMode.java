package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

/**
 * DataX Kubernetes cleanup mode.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
public enum DataxKubernetesCleanupMode {

    /**
     * Cleanup old runtime resources before submit.
     */
    BEFORE_SUBMIT,

    /**
     * Cleanup runtime resources after final state report.
     */
    AFTER_FINISH
}
