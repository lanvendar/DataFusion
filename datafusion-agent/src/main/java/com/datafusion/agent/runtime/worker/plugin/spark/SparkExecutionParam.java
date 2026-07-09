package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.agent.runtime.worker.plugin.spark.k8s.SparkKubernetesParam;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Spark 执行参数.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Data
@Builder
public class SparkExecutionParam {

    /**
     * 运行模式.
     */
    private SparkRunMode runMode;

    /**
     * 流程实例 ID.
     */
    private String flowInstanceId;

    /**
     * 任务实例 ID.
     */
    private String taskInstanceId;

    /**
     * 任务运行目录.
     */
    private Path workDir;

    /**
     * 有效任务数据.
     */
    private JsonNode effectiveTaskData;

    /**
     * Spark 版本.
     */
    private String sparkVersion;

    /**
     * Spark 应用 main class.
     */
    private String mainClass;

    /**
     * Spark main application file.
     */
    private String mainApplicationFile;

    /**
     * Spark 配置.
     */
    @Builder.Default
    private Map<String, String> sparkConf = Collections.emptyMap();

    /**
     * Hadoop 配置.
     */
    @Builder.Default
    private Map<String, String> hadoopConf = Collections.emptyMap();

    /**
     * 应用参数.
     */
    @Builder.Default
    private List<String> arguments = Collections.emptyList();

    /**
     * Kubernetes 参数.
     */
    private SparkKubernetesParam kubernetes;
}
