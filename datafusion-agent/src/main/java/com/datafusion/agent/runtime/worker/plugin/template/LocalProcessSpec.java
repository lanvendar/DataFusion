package com.datafusion.agent.runtime.worker.plugin.template;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Local process execution spec.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/9
 * @since 1.0.0
 */
@Data
public class LocalProcessSpec {

    /**
     * Spec kind.
     */
    private String kind;

    /**
     * Process work directory.
     */
    private String workDir;

    /**
     * Command line.
     */
    private List<String> command = Collections.emptyList();

    /**
     * Process environment variables.
     */
    private Map<String, String> env = Collections.emptyMap();

    /**
     * Standard output file.
     */
    private String stdout;

    /**
     * Standard error file.
     */
    private String stderr;

    /**
     * Plugin log URI.
     */
    private String pluginLogUri;
}
