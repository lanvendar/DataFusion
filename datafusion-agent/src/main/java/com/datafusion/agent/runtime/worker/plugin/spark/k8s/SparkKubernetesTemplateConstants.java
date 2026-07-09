package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

/**
 * Spark Kubernetes 模板常量.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
public final class SparkKubernetesTemplateConstants {

    /**
     * 模板路径.
     */
    public static final String TEMPLATE_PATH = "spark/templates/spark-k8s-operator-application.yml";

    /**
     * Spark SQL job 文件名.
     */
    public static final String JOB_JSON_FILE_NAME = "spark-sql-job.json";

    /**
     * 渲染后的 manifest 文件名.
     */
    public static final String MANIFEST_FILE_NAME = "spark-k8s-operator-application.yml";

    private SparkKubernetesTemplateConstants() {
    }
}
