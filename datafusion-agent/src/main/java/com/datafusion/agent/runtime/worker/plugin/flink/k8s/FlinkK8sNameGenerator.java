package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.utils.KubernetesResourceNameUtils;

import java.util.Locale;

/**
 * Flink Kubernetes resource name generator.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
public final class FlinkK8sNameGenerator {

    /**
     * Plugin label.
     */
    public static final String PLUGIN_LABEL = "datafusion.io/plugin-type";

    /**
     * Run mode label.
     */
    public static final String RUN_MODE_LABEL = "datafusion.io/run-mode";

    /**
     * Task label.
     */
    public static final String TASK_LABEL = "datafusion.io/task-instance-id";

    /**
     * Flow label.
     */
    public static final String FLOW_LABEL = "datafusion.io/flow-instance-id";

    /**
     * Maximum DNS label length.
     */
    private static final int DNS_LABEL_MAX_LENGTH = 63;

    /**
     * 作业配置资源角色.
     */
    private static final String JOB_CONFIG_ROLE = "job-config";

    private FlinkK8sNameGenerator() {
    }

    /**
     * Generate deployment name.
     *
     * @param prefix         name prefix
     * @param taskInstanceId task instance ID
     * @return deployment name
     */
    public static String deploymentName(String prefix, String taskInstanceId) {
        return KubernetesResourceNameUtils.resourceName(prefix, taskInstanceId);
    }

    /**
     * Generate secret name.
     *
     * @param prefix         name prefix
     * @param taskInstanceId task instance ID
     * @return secret name
     */
    public static String secretName(String prefix, String taskInstanceId) {
        return KubernetesResourceNameUtils.resourceName(prefix, JOB_CONFIG_ROLE, taskInstanceId);
    }

    /**
     * Build pod label selector.
     *
     * @param taskInstanceId task instance ID
     * @return pod label selector
     */
    public static String podLabelSelector(String taskInstanceId) {
        return TASK_LABEL + "=" + labelValue(taskInstanceId);
    }

    /**
     * Normalize label value.
     *
     * @param value raw value
     * @return normalized label value
     */
    public static String labelValue(String value) {
        String normalized = normalize(value);
        if (normalized.length() > DNS_LABEL_MAX_LENGTH) {
            return normalized.substring(0, DNS_LABEL_MAX_LENGTH);
        }
        return normalized;
    }

    private static String normalize(String value) {
        String text = value == null || value.trim().isEmpty() ? "unknown" : value.trim();
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.isEmpty() ? "unknown" : normalized;
    }
}
