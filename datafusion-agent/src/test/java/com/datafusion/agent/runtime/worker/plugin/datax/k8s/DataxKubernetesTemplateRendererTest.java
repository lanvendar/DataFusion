package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DataxKubernetesTemplateRenderer}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
class DataxKubernetesTemplateRendererTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRenderSecretAndJobYamlFromTemplate() {
        DataxKubernetesTemplateRenderer renderer = new DataxKubernetesTemplateRenderer(
                new TemplateSpecRenderer(pluginsProperties()));
        String yaml = renderer.render(param(), "{\"job\":{\"content\":[]}}");

        assertTrue(yaml.contains("kind: Secret"));
        assertTrue(yaml.contains("kind: Job"));
        assertTrue(yaml.contains("name: \"df-datax-task-1\""));
        assertTrue(yaml.contains("image: \"datafusion/datax:latest\""));
        assertTrue(yaml.contains("DATAX_JOB_FILE"));
        assertTrue(yaml.contains("/opt/datafusion/plugins/datax/job/job.json"));
        assertTrue(yaml.contains("/opt/datafusion/plugins/datax/logs/datax.log"));
        assertTrue(yaml.contains("/opt/datafusion/plugins/datax"));
        assertTrue(yaml.contains("DATAX_JOB_ID"));
        assertTrue(yaml.contains("JAVA_OPTS"));
        assertTrue(yaml.contains("--add-opens java.base/java.lang=ALL-UNNAMED"));
        assertTrue(yaml.contains("\"cpu\": \"1\""));
    }

    private DataxExecutionParam param() {
        return DataxExecutionParam.builder()
                .flowInstanceId("flow-1")
                .logLevel("INFO")
                .logMaxSize("100MB")
                .logMaxIndex(100)
                .jobId("-1")
                .jvmOptions(List.of("--add-opens", "java.base/java.lang=ALL-UNNAMED"))
                .kubernetes(kubernetes())
                .build();
    }

    private DataxKubernetesParam kubernetes() {
        ObjectNode requests = OBJECT_MAPPER.createObjectNode();
        requests.put("cpu", "1");
        ObjectNode resources = OBJECT_MAPPER.createObjectNode();
        resources.set("requests", requests);
        return DataxKubernetesParam.builder()
                .namespace("df")
                .jobName("df-datax-task-1")
                .secretName("df-datax-job-task-1")
                .podLabelSelector("datafusion.io/task-instance-id=task-1")
                .image("datafusion/datax:latest")
                .imagePullPolicy("IfNotPresent")
                .backoffLimit(0)
                .ttlSecondsAfterFinished(86400)
                .jobJsonMountPath(DataxKubernetesTemplateConstants.JOB_JSON_MOUNT_PATH)
                .dataxHome(DataxKubernetesTemplateConstants.DATAX_HOME)
                .containerName(DataxKubernetesTemplateConstants.CONTAINER_NAME)
                .resources(resources)
                .env(Map.of("JAVA_OPTS", "-Xmx1g"))
                .build();
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
            return moduleResourceDir.toString();
        }
        return workingDir.resolve("datafusion-agent/src/main/resources").toString();
    }
}
