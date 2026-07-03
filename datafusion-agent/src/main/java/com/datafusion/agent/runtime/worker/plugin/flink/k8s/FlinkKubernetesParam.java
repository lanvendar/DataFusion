package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Flink Kubernetes execution parameter.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Data
@Builder
public class FlinkKubernetesParam {

    /**
     * Kubernetes namespace.
     */
    private String namespace;

    /**
     * Runtime image.
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
     * Shared PVC name.
     */
    private String sharedPvcName;

    /**
     * Shared PVC mount path.
     */
    private String sharedMountPath;

    /**
     * Deployment name.
     */
    private String deploymentName;

    /**
     * Flink web UI URI.
     */
    private String flinkWebUiUri;

    /**
     * Upgrade mode.
     */
    private String upgradeMode;

    /**
     * External log storage URI.
     */
    private String logStorageUri;

    /**
     * Whether collect logs on finish.
     */
    private boolean collectLogsOnFinish;

    /**
     * Whether delete deployment on finish.
     */
    private boolean deleteDeploymentOnFinish;

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
     * Environment variables from ConfigMap or Secret.
     */
    private JsonNode envFrom;

    /**
     * Node selector.
     */
    @Builder.Default
    private Map<String, String> nodeSelector = Collections.emptyMap();

    /**
     * Image pull secrets.
     */
    private JsonNode imagePullSecrets;

    /**
     * JobManager replicas.
     */
    private Integer jobManagerReplicas;

    /**
     * JobManager resource.
     */
    private JsonNode jobManagerResource;

    /**
     * TaskManager replicas.
     */
    private Integer taskManagerReplicas;

    /**
     * TaskManager resource.
     */
    private JsonNode taskManagerResource;

    /**
     * Pod label selector.
     */
    private String podLabelSelector;

    /**
     * Flink app directory in Pod.
     */
    private String flinkAppDir;

    /**
     * Jar URI.
     */
    private String jarUri;

    /**
     * Container args.
     */
    @Builder.Default
    private List<String> args = Collections.emptyList();

    /**
     * Job parallelism.
     */
    private Integer jobParallelism;

    /**
     * Job state.
     */
    private String jobState;
}
