package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import java.util.Locale;

/**
 * Spark Kubernetes 资源名称生成器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
public final class SparkK8sNameGenerator {

    /**
     * 插件类型 label.
     */
    public static final String PLUGIN_LABEL = "datafusion.io/plugin-type";

    /**
     * 运行模式 label.
     */
    public static final String RUN_MODE_LABEL = "datafusion.io/run-mode";

    /**
     * 任务实例 label.
     */
    public static final String TASK_LABEL = "datafusion.io/task-instance-id";

    /**
     * 流程实例 label.
     */
    public static final String FLOW_LABEL = "datafusion.io/flow-instance-id";

    /**
     * DNS label 最大长度.
     */
    private static final int DNS_LABEL_MAX_LENGTH = 63;

    private SparkK8sNameGenerator() {
    }

    /**
     * 生成 SparkApplication 名称.
     *
     * @param prefix         名称前缀
     * @param taskInstanceId 任务实例 ID
     * @return SparkApplication 名称
     */
    public static String applicationName(String prefix, String taskInstanceId) {
        return dnsName(prefix, taskInstanceId);
    }

    /**
     * 生成 ConfigMap 名称.
     *
     * @param prefix         名称前缀
     * @param taskInstanceId 任务实例 ID
     * @return ConfigMap 名称
     */
    public static String configMapName(String prefix, String taskInstanceId) {
        return dnsName(prefix, taskInstanceId);
    }

    /**
     * 生成 Pod label selector.
     *
     * @param taskInstanceId 任务实例 ID
     * @return label selector
     */
    public static String podLabelSelector(String taskInstanceId) {
        return TASK_LABEL + "=" + labelValue(taskInstanceId);
    }

    /**
     * 归一化 label value.
     *
     * @param value 原始值
     * @return label value
     */
    public static String labelValue(String value) {
        String normalized = normalize(value);
        if (normalized.length() > DNS_LABEL_MAX_LENGTH) {
            return normalized.substring(0, DNS_LABEL_MAX_LENGTH);
        }
        return normalized;
    }

    private static String dnsName(String prefix, String value) {
        String normalized = normalize((prefix == null ? "" : prefix) + value);
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
