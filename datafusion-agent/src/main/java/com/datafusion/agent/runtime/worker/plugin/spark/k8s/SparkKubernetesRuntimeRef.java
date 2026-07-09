package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spark Kubernetes 运行引用.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparkKubernetesRuntimeRef {

    /**
     * Kubernetes namespace.
     */
    private String namespace;

    /**
     * SparkApplication 名称.
     */
    private String applicationName;

    /**
     * ConfigMap 名称.
     */
    private String configMapName;

    /**
     * Pod label selector.
     */
    private String podLabelSelector;

    /**
     * 外部日志 URI.
     */
    private String logStorageUri;

    /**
     * Spark Web UI URI.
     */
    private String sparkWebUiUri;

    /**
     * 是否终态采集日志.
     */
    private boolean collectLogsOnFinish;

}
