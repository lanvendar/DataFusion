package com.datafusion.agent.runtime.worker.plugin.datax;

/**
 * DataX run mode.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public enum DataxRunMode {

    /**
     * Local process mode.
     */
    LOCAL,

    /**
     * Kubernetes Job mode.
     */
    K8S;

    /**
     * Parse run mode.
     *
     * @param value run mode text
     * @return run mode
     */
    public static DataxRunMode parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("pluginParam.runMode不能为空");
        }
        for (DataxRunMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("不支持的DataX运行模式: " + value);
    }
}
