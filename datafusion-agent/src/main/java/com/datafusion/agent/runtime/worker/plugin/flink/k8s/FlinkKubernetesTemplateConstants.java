package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

/**
 * Flink Kubernetes template constants.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
public final class FlinkKubernetesTemplateConstants {

    /**
     * Template path.
     */
    public static final String TEMPLATE_PATH = "flink/templates/flink-k8s-operator-deployment.yml";

    /**
     * Flink job JSON file name.
     */
    public static final String JOB_JSON_FILE_NAME = "flink-job.json";

    /**
     * Rendered FlinkDeployment manifest file name.
     */
    public static final String MANIFEST_FILE_NAME = "flink-k8s-operator-deployment.yml";

    /**
     * Flink usrlib path.
     */
    public static final String USRLIB_PATH = "/opt/flink/usrlib";

    private FlinkKubernetesTemplateConstants() {
    }
}
