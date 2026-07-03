package com.datafusion.agent.runtime.worker.plugin.flink;

/**
 * Flink run mode.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
public enum FlinkRunMode {

    /**
     * Local mode.
     */
    LOCAL,

    /**
     * Standalone mode.
     */
    STANDALONE,

    /**
     * Flink on YARN.
     */
    YARN,

    /**
     * Flink native Kubernetes.
     */
    K8S,

    /**
     * Flink Kubernetes Operator.
     */
    K8S_OPERATOR;

    /**
     * Parse run mode.
     *
     * @param value run mode value
     * @return run mode
     */
    public static FlinkRunMode parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("pluginParam.runMode不能为空");
        }
        return FlinkRunMode.valueOf(value.trim().toUpperCase());
    }
}
