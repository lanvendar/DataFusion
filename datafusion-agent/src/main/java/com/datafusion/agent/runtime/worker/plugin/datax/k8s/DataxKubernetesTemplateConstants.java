package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

/**
 * DataX Kubernetes template constants.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public final class DataxKubernetesTemplateConstants {

    /**
     * Classpath template location.
     */
    public static final String TEMPLATE_PATH = "plugins/datax/templates/datax-k8s-runtime.yml";

    /**
     * DataX Job secret key.
     */
    public static final String JOB_SECRET_KEY = "job.json";

    /**
     * Job JSON mount path inside container.
     */
    public static final String JOB_JSON_MOUNT_PATH = "/datafusion/job/job.json";

    /**
     * Main DataX container name.
     */
    public static final String CONTAINER_NAME = "datax";

    /**
     * DataX home inside container.
     */
    public static final String DATAX_HOME = "/opt/datafusion/datax";

    private DataxKubernetesTemplateConstants() {
    }
}
