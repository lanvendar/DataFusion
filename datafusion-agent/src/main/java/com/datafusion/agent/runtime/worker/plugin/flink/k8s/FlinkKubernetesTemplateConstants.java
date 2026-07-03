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
    public static final String TEMPLATE_PATH = "plugins/flink/templates/flink-k8s-operator-deployment.yml";

    /**
     * Job JSON secret key.
     */
    public static final String JOB_JSON_SECRET_KEY = "job.json";

    /**
     * Job JSON mount path.
     */
    public static final String JOB_JSON_MOUNT_PATH = "/opt/datafusion/flink/job/job.json";

    /**
     * Job JSON mount directory.
     */
    public static final String JOB_JSON_MOUNT_DIR = "/opt/datafusion/flink/job";

    /**
     * Flink usrlib path.
     */
    public static final String USRLIB_PATH = "/opt/flink/usrlib";

    /**
     * Main Flink container name.
     */
    public static final String FLINK_MAIN_CONTAINER_NAME = "flink-main-container";

    /**
     * Init container name.
     */
    public static final String INIT_CONTAINER_NAME = "datafusion-flink-usrlib";

    private FlinkKubernetesTemplateConstants() {
    }
}
