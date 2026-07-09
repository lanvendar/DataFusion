package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.scheduler.enums.StatusEnum;

/**
 * Flink Kubernetes Operator client.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
public interface K8sOperatorClient {

    /**
     * Submit FlinkDeployment.
     *
     * @param param execution parameter
     * @return runtime reference
     */
    FlinkKubernetesRuntimeRef submit(FlinkExecutionParam param);

    /**
     * Stop FlinkDeployment.
     *
     * @param runtimeRef runtime reference
     */
    void stop(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * Kill FlinkDeployment.
     *
     * @param runtimeRef runtime reference
     */
    void kill(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * Query status.
     *
     * @param runtimeRef runtime reference
     * @param localState local state
     * @return status
     */
    StatusEnum queryStatus(FlinkKubernetesRuntimeRef runtimeRef, StatusEnum localState);

    /**
     * Collect logs.
     *
     * @param runtimeRef runtime reference
     * @return log content
     */
    String collectLogs(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * Cleanup runtime resources.
     *
     * @param runtimeRef runtime reference
     * @return true if cleanup completed
     */
    boolean cleanup(FlinkKubernetesRuntimeRef runtimeRef);
}
