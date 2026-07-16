package com.datafusion.agent.runtime.worker.plugin.spark;

/**
 * Spark 运行模式.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
public enum SparkRunMode {

    /**
     * Kubernetes Spark Operator 模式.
     */
    K8S_OPERATOR;

    /**
     * 解析运行模式.
     *
     * @param value 原始值
     * @return 运行模式
     */
    public static SparkRunMode parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("runMode不能为空");
        }
        for (SparkRunMode runMode : values()) {
            if (runMode.name().equalsIgnoreCase(value.trim())) {
                return runMode;
            }
        }
        throw new IllegalArgumentException("不支持的Spark运行模式: " + value);
    }
}
