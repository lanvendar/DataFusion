package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesParam;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DataX normalized execution parameter.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
@Data
@Builder
public class DataxExecutionParam {

    /**
     * Run mode.
     */
    private DataxRunMode runMode;

    /**
     * Flow instance ID.
     */
    private String flowInstanceId;

    /**
     * Task instance ID.
     */
    private String taskInstanceId;

    /**
     * Job file name.
     */
    private String jobName;

    /**
     * Inline DataX job JSON.
     */
    private JsonNode jobJson;

    /**
     * Original plugin parameter snapshot.
     */
    private JsonNode pluginParam;

    /**
     * Existing job file name under resources job directory.
     */
    private String jobFileName;

    /**
     * Existing local job path.
     */
    private String jobPath;

    /**
     * Local log directory.
     */
    private Path logDir;

    /**
     * Local work directory.
     */
    private Path workDir;

    /**
     * DataX resources root.
     */
    private String resourcesRoot;

    /**
     * DataX home.
     */
    private String dataxHome;

    /**
     * DataX bundle jar.
     */
    private String dataxJar;

    /**
     * DataX logback config file.
     */
    private String logbackConfigFile;

    /**
     * Java binary.
     */
    private String javaBin;

    /**
     * DataX log file.
     */
    private Path logFile;

    /**
     * DataX log level.
     */
    private String logLevel;

    /**
     * DataX log max size.
     */
    private String logMaxSize;

    /**
     * DataX log max index.
     */
    private int logMaxIndex;

    /**
     * Local job file permissions.
     */
    private String writeJobFilePermissions;

    /**
     * Environment variables.
     */
    @Builder.Default
    private Map<String, String> env = Collections.emptyMap();

    /**
     * JVM options.
     */
    @Builder.Default
    private List<String> jvmOptions = Collections.emptyList();

    /**
     * DataX engine arguments.
     */
    @Builder.Default
    private List<String> dataxArgs = Collections.emptyList();

    /**
     * Kubernetes parameter.
     */
    private DataxKubernetesParam kubernetes;
}
