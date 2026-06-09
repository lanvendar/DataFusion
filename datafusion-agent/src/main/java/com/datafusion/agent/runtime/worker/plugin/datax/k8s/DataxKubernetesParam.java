package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * DataX Kubernetes execution parameter.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Data
@Builder
public class DataxKubernetesParam {

    /**
     * Kubernetes namespace.
     */
    private String namespace;

    /**
     * DataX image.
     */
    private String image;

    /**
     * Image pull policy.
     */
    private String imagePullPolicy;

    /**
     * Service account.
     */
    private String serviceAccountName;

    /**
     * Backoff limit.
     */
    private int backoffLimit;

    /**
     * Active deadline seconds.
     */
    private Long activeDeadlineSeconds;

    /**
     * TTL after finished.
     */
    private Integer ttlSecondsAfterFinished;

    /**
     * Job name.
     */
    private String jobName;

    /**
     * Secret name.
     */
    private String secretName;

    /**
     * Job JSON mount path.
     */
    private String jobJsonMountPath;

    /**
     * Container DataX home.
     */
    private String dataxHome;

    /**
     * Container name.
     */
    private String containerName;

    /**
     * External log storage URI.
     */
    private String logStorageUri;

    /**
     * Whether collect logs on finish.
     */
    private boolean collectLogsOnFinish;

    /**
     * Whether delete Job on finish.
     */
    private boolean deleteJobOnFinish;

    /**
     * User labels.
     */
    @Builder.Default
    private Map<String, String> labels = Collections.emptyMap();

    /**
     * User annotations.
     */
    @Builder.Default
    private Map<String, String> annotations = Collections.emptyMap();

    /**
     * Environment variables.
     */
    @Builder.Default
    private Map<String, String> env = Collections.emptyMap();

    /**
     * Node selector.
     */
    @Builder.Default
    private Map<String, String> nodeSelector = Collections.emptyMap();

    /**
     * Container resources.
     */
    private JsonNode resources;

    /**
     * Pod label selector.
     */
    private String podLabelSelector;
}
