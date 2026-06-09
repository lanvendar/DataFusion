package com.datafusion.agent.runtime.worker.plugin.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TemplateSpecRenderer}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/9
 * @since 1.0.0
 */
class TemplateSpecRendererTest {

    @Test
    void shouldRenderShellLocalProcessSpec() {
        TemplateSpecRenderer renderer = new TemplateSpecRenderer();
        LocalProcessSpec spec = renderer.renderYaml("templates/shell/shell-local.yml", Map.of(
                "workDir", "/tmp",
                "command", TemplateYamlFragments.listItems(java.util.List.of("bash", "-lc", "echo ok"), 2),
                "env", TemplateYamlFragments.mapEntries(Map.of("A", "B"), 2),
                "stdout", "/tmp/stdout.log",
                "stderr", "/tmp/stderr.log",
                "pluginLogUri", "oss://logs/shell/task-1"
        ), LocalProcessSpec.class);

        assertEquals("LocalProcessSpec", spec.getKind());
        assertEquals("bash", spec.getCommand().get(0));
        assertEquals("B", spec.getEnv().get("A"));
        assertTrue(spec.getStdout().endsWith("stdout.log"));
    }

    @Test
    void shouldRenderDataxLocalProcessSpec() {
        TemplateSpecRenderer renderer = new TemplateSpecRenderer();
        LocalProcessSpec spec = renderer.renderYaml("templates/datax/datax-local.yml", Map.ofEntries(
                Map.entry("workDir", "/tmp/datax-work"),
                Map.entry("javaBin", "java"),
                Map.entry("jvmOptions", TemplateYamlFragments.listItems(java.util.List.of("-Xmx1g"), 2)),
                Map.entry("dataxHome", "/opt/datax"),
                Map.entry("logLevel", "INFO"),
                Map.entry("logFile", "/tmp/datax.log"),
                Map.entry("logMaxSize", "100MB"),
                Map.entry("logMaxIndex", "100"),
                Map.entry("logbackConfigFile", "/opt/datax/conf/logback.xml"),
                Map.entry("dataxJar", "/opt/datax/lib/datax-bundle.jar"),
                Map.entry("jobFile", "/tmp/job.json"),
                Map.entry("dataxArgs", TemplateYamlFragments.listItems(java.util.List.of("-Dfoo=bar"), 2)),
                Map.entry("env", TemplateYamlFragments.mapEntries(Map.of("BIZ_DATE", "20260609"), 2)),
                Map.entry("stdout", "/tmp/stdout.log"),
                Map.entry("stderr", "/tmp/stderr.log")
        ), LocalProcessSpec.class);

        assertEquals("java", spec.getCommand().get(0));
        assertTrue(spec.getCommand().contains("com.alibaba.datax.core.Engine"));
        assertEquals("20260609", spec.getEnv().get("BIZ_DATE"));
        assertEquals("INFO", spec.getEnv().get("DATAX_LOG_LEVEL"));
    }
}
