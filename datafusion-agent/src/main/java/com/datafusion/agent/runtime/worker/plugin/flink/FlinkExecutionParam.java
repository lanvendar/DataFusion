package com.datafusion.agent.runtime.worker.plugin.flink;

import com.datafusion.agent.runtime.worker.plugin.flink.k8s.FlinkKubernetesParam;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Flink normalized execution parameter.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Data
@Builder
public class FlinkExecutionParam {

    /**
     * Run mode.
     */
    private FlinkRunMode runMode;

    /**
     * Flow instance ID.
     */
    private String flowInstanceId;

    /**
     * Task instance ID.
     */
    private String taskInstanceId;

    /**
     * Inline job JSON.
     */
    private JsonNode jobJson;

    /**
     * Effective task data.
     */
    private JsonNode effectiveTaskData;

    /**
     * Work directory.
     */
    private Path workDir;

    /**
     * Effective Flink configuration.
     */
    @Builder.Default
    private Map<String, String> flinkConfig = Collections.emptyMap();

    /**
     * Flink app directory in Pod.
     */
    private String flinkAppDir;

    /**
     * Launch mode.
     */
    private String launchMode;

    /**
     * Flink app jar.
     */
    private String flinkAppJar;

    /**
     * Extra classpath expression.
     */
    private String classpath;

    /**
     * Main class.
     */
    private String mainClass;

    /**
     * Flink version.
     */
    private String flinkVersion;

    /**
     * Dependency lib directory.
     */
    private String libDir;

    /**
     * Launch args.
     */
    @Builder.Default
    private List<String> args = Collections.emptyList();

    /**
     * Kubernetes parameter.
     */
    private FlinkKubernetesParam kubernetes;
}
