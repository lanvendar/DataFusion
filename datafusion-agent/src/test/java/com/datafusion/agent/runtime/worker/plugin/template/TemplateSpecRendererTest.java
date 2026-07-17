package com.datafusion.agent.runtime.worker.plugin.template;

import com.datafusion.agent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
        TemplateSpecRenderer renderer = new TemplateSpecRenderer(pluginsProperties());
        LocalProcessSpec spec = renderer.renderYaml("shell/templates/shell-local-runtime.yml", Map.of(
                "command", TemplateYamlFragments.listItems(java.util.List.of("bash", "-lc", "echo ok"), 2)
        ), LocalProcessSpec.class);

        assertEquals("LocalShellProcess", spec.getKind());
        assertEquals("bash", spec.getCommand().get(0));
        assertEquals("echo ok", spec.getCommand().get(2));
    }

    @Test
    void shouldRenderDataxLocalProcessSpec() {
        TemplateSpecRenderer renderer = new TemplateSpecRenderer(pluginsProperties());
        LocalProcessSpec spec = renderer.renderYaml("datax/templates/datax-local-runtime.yml", Map.ofEntries(
                Map.entry("javaBin", "java"),
                Map.entry("jvmOptions", TemplateYamlFragments.listItems(java.util.List.of("-Xmx1g"), 2)),
                Map.entry("dataxHome", "/opt/datax"),
                Map.entry("logLevel", "INFO"),
                Map.entry("logFile", "/tmp/datax.log"),
                Map.entry("logMaxSize", "100MB"),
                Map.entry("logMaxIndex", "100"),
                Map.entry("logConfigFile", "/opt/datax/conf/logback.xml"),
                Map.entry("dataxJar", "/opt/datax/lib/datax-bundle.jar"),
                Map.entry("mainClass", "com.alibaba.datax.core.Engine"),
                Map.entry("jobMode", "standalone"),
                Map.entry("jobId", "-1"),
                Map.entry("jobFile", "/tmp/job.json")
        ), LocalProcessSpec.class);

        assertEquals("LocalShellProcess", spec.getKind());
        assertEquals("java", spec.getCommand().get(0));
        assertTrue(spec.getCommand().contains("-Xmx1g"));
        assertTrue(spec.getCommand().contains("com.alibaba.datax.core.Engine"));
        assertTrue(spec.getCommand().contains("/tmp/job.json"));
    }

    private AgentProperties pluginsProperties() {
        AgentProperties properties = new AgentProperties();
        properties.setPluginsRootDir(resolvePluginsRootDir());
        return properties;
    }

    private String resolvePluginsRootDir() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path moduleResourceDir = workingDir.resolve("src/main/resources");
        if (Files.isDirectory(moduleResourceDir)) {
            return moduleResourceDir.resolve("plugins").toString();
        }
        return workingDir.resolve("datafusion-agent/src/main/resources/plugins").toString();
    }
}
