package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * Spark Kubernetes 参数.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Data
@Builder
public class SparkKubernetesParam {

    /**
     * Kubernetes namespace.
     */
    private String namespace;

    /**
     * SparkApplication 名称.
     */
    private String applicationName;

    /**
     * SQL job ConfigMap 名称.
     */
    private String configMapName;

    /**
     * 镜像.
     */
    private String image;

    /**
     * 镜像拉取策略.
     */
    private String imagePullPolicy;

    /**
     * ServiceAccount 名称.
     */
    private String serviceAccountName;

    /**
     * 共享插件目录.
     */
    private String pluginAppDir;

    /**
     * 共享 PVC 名称.
     */
    private String sharedPvcName;

    /**
     * 共享 PVC 挂载路径.
     */
    private String sharedMountPath;

    /**
     * 插件 jar 名称.
     */
    private String pluginJarName;

    /**
     * Pod 内 jar 目录.
     */
    private String jarMountPath;

    /**
     * Pod 内 job 配置路径.
     */
    private String jobConfigMountPath;

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

    /**
     * 用户 label.
     */
    @Builder.Default
    private Map<String, String> labels = Collections.emptyMap();

    /**
     * 用户 annotation.
     */
    @Builder.Default
    private Map<String, String> annotations = Collections.emptyMap();

    /**
     * Node selector.
     */
    @Builder.Default
    private Map<String, String> nodeSelector = Collections.emptyMap();

    /**
     * Driver 配置.
     */
    private JsonNode driver;

    /**
     * Executor 配置.
     */
    private JsonNode executor;
}
