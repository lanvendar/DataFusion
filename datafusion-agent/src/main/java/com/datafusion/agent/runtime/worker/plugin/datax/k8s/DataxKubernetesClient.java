package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.datafusion.scheduler.enums.StatusEnum;

/**
 * DataX Kubernetes client.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public interface DataxKubernetesClient {

    /**
     * Submit Kubernetes Job.
     *
     * @param param execution param
     * @return runtime reference
     */
    DataxKubernetesRuntimeRef submit(DataxExecutionParam param);

    /**
     * Stop Kubernetes Job.
     *
     * @param runtimeRef runtime reference
     * @param forcibly   forcibly delete pods
     */
    void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly);

    /**
     * Query status.
     *
     * @param runtimeRef runtime reference
     * @param localState local state
     * @return mapped status
     */
    StatusEnum queryStatus(DataxKubernetesRuntimeRef runtimeRef, StatusEnum localState);

    /**
     * Collect logs.
     *
     * @param runtimeRef runtime reference
     * @return logs
     */
    String collectLogs(DataxKubernetesRuntimeRef runtimeRef);

    /**
     * Cleanup resources.
     *
     * @param runtimeRef runtime reference
     * @param mode       cleanup mode
     * @return true if cleanup completed
     */
    boolean cleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode);
}
