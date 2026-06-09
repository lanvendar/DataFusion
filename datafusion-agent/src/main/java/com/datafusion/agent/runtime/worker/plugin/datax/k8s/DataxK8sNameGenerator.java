package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * DataX Kubernetes name generator.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public final class DataxK8sNameGenerator {

    /**
     * Task instance label.
     */
    public static final String TASK_LABEL = "datafusion.io/task-instance-id";

    /**
     * Flow instance label.
     */
    public static final String FLOW_LABEL = "datafusion.io/flow-instance-id";

    /**
     * Plugin type label.
     */
    public static final String PLUGIN_LABEL = "datafusion.io/plugin-type";

    /**
     * Run mode label.
     */
    public static final String RUN_MODE_LABEL = "datafusion.io/run-mode";

    /**
     * Max Kubernetes name length.
     */
    private static final int MAX_NAME_LENGTH = 63;

    private DataxK8sNameGenerator() {
    }

    /**
     * Generate Job name.
     *
     * @param prefix         prefix
     * @param taskInstanceId task instance id
     * @return Job name
     */
    public static String jobName(String prefix, String taskInstanceId) {
        return dnsName(prefix, taskInstanceId);
    }

    /**
     * Generate Secret name.
     *
     * @param prefix         prefix
     * @param taskInstanceId task instance id
     * @return Secret name
     */
    public static String secretName(String prefix, String taskInstanceId) {
        return dnsName(prefix, taskInstanceId);
    }

    /**
     * Pod label selector.
     *
     * @param taskInstanceId task instance id
     * @return selector
     */
    public static String podLabelSelector(String taskInstanceId) {
        return TASK_LABEL + "=" + labelValue(taskInstanceId);
    }

    /**
     * Kubernetes label value.
     *
     * @param value raw value
     * @return label value
     */
    public static String labelValue(String value) {
        String normalized = normalize(value);
        if (normalized.length() <= MAX_NAME_LENGTH) {
            return normalized;
        }
        String hash = hash(value).substring(0, 10);
        return normalized.substring(0, MAX_NAME_LENGTH - hash.length() - 1) + "-" + hash;
    }

    private static String dnsName(String prefix, String value) {
        String rawPrefix = prefix == null ? "" : prefix;
        String base = normalize(rawPrefix + value);
        if (base.length() <= MAX_NAME_LENGTH) {
            return trimEdge(base);
        }
        String hash = hash(value).substring(0, 10);
        int maxBaseLength = MAX_NAME_LENGTH - hash.length() - 1;
        return trimEdge(base.substring(0, maxBaseLength)) + "-" + hash;
    }

    private static String normalize(String value) {
        String text = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^a-z0-9.-]", "-");
        text = text.replaceAll("-+", "-");
        return trimEdge(text);
    }

    private static String trimEdge(String value) {
        String text = value;
        while (text.startsWith("-") || text.startsWith(".")) {
            text = text.substring(1);
        }
        while (text.endsWith("-") || text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isEmpty() ? "unknown" : text;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(String.valueOf(value).hashCode());
        }
    }
}
