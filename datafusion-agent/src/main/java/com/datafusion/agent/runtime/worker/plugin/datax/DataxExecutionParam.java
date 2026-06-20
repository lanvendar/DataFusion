package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.runtime.worker.plugin.datax.k8s.DataxKubernetesParam;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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
     * Inline DataX job JSON.
     */
    private JsonNode jobJson;

    /**
     * Effective DataX task data.
     */
    private JsonNode effectiveTaskData;

    /**
     * Existing DataX job file path from plugin parameter.
     */
    private String jobFile;

    /**
     * Local work directory.
     */
    private Path workDir;

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
     * DataX main class.
     */
    private String mainClass;

    /**
     * DataX job mode.
     */
    private String jobMode;

    /**
     * DataX job ID.
     */
    private String jobId;

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
     * JVM options.
     */
    @Builder.Default
    private List<String> jvmOptions = Collections.emptyList();

    /**
     * Kubernetes parameter.
     */
    private DataxKubernetesParam kubernetes;
}
