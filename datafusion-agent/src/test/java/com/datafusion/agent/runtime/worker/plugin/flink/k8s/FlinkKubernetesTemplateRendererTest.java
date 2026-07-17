package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.flink.FlinkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FlinkKubernetesTemplateRenderer}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
class FlinkKubernetesTemplateRendererTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRenderOperatorYamlWithDerivedRuntimeDefaults() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setPluginsRootDir(resolvePluginsRootDir());
        FlinkParamResolver resolver = new FlinkParamResolver();
        FlinkExecutionParam param = resolver.resolve(snapshot(), "target/flink-task-1");
        FlinkKubernetesTemplateRenderer renderer = new FlinkKubernetesTemplateRenderer(new TemplateSpecRenderer(properties));
        String yaml = renderer.render(param, OBJECT_MAPPER.writeValueAsString(param.getEffectiveTaskData()));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/flink-k8s-operator-generated.yml"), yaml, StandardCharsets.UTF_8);

        assertTrue(yaml.contains("kind: FlinkDeployment"));
        assertTrue(yaml.contains("- \"--job\""));
        assertTrue(yaml.contains("http://custom-flink-task-1-rest.datafusion.svc:8081"));
        assertTrue(yaml.contains("s3a://data-lake-warehouse/flink/checkpoints/kafka-json-job"));
        assertTrue(yaml.contains("s3a://data-lake-warehouse/flink/savepoints/kafka-json-job"));
        assertTrue(yaml.contains("\"fs.s3a.endpoint\": \"172.26.185.200\""));
        assertTrue(yaml.contains("\"fs.s3a.path.style.access\": \"true\""));
        assertTrue(yaml.contains("fs.s3a.aws.credentials.provider"));
        assertTrue(yaml.contains("envFrom:"));
        assertTrue(yaml.contains("flink-objectstore"));
        assertTrue(yaml.contains("replicas: 1"));
        assertTrue(yaml.contains("\"cpu\": 1.0"));
        assertTrue(yaml.contains("\"cpu\": 2.0"));
        assertTrue(yaml.contains("\"state.backend\": \"hashmap\""));
        assertTrue(yaml.contains("parallelism: 2"));
        assertTrue(yaml.contains("upgradeMode: \"savepoint\""));
        assertTrue(yaml.contains("state: \"running\""));
        assertFalse(yaml.contains("state: \"suspended\""));
        assertFalse(yaml.contains("kind: Secret"));
        assertFalse(yaml.contains("datafusion-flink-job"));
        assertFalse(yaml.contains("flink-job.json"));
        assertFalse(yaml.contains("--jobFile"));
        assertFalse(yaml.contains("state.backend.type"));
        assertFalse(yaml.contains("must-not-render"));
    }

    private WorkerTaskExecutionSnap snapshot() {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("Flink")
                .pluginType("FLINK")
                .runMode("K8S_OPERATOR")
                .pluginParam(pluginParam())
                .taskData(taskData())
                .build();
    }

    private ObjectNode pluginParam() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("flinkAppDir", "/opt/datafusion/plugins/flink/datafusion-plugin-flink-table");
        pluginParam.put("launchMode", "JAR");
        pluginParam.put("flinkAppJar", "datafusion-plugin-flink-table-1.0.0-executable.jar");
        pluginParam.put("mainClass", "com.datafusion.plugin.flink.table.FlinkTablePaimonApplication");
        pluginParam.put("flinkVersion", "2.2.0");
        pluginParam.put("libDir", "lib");
        pluginParam.put("flinkCheckpointRootDir", "s3a://data-lake-warehouse/flink/");
        ObjectNode flinkConfig = OBJECT_MAPPER.createObjectNode();
        flinkConfig.put("state.backend", "rocksdb");
        flinkConfig.put("parallelism.default", "2");
        flinkConfig.put("fs.s3a.endpoint", "172.26.185.200");
        flinkConfig.put("fs.s3a.path.style.access", "true");
        flinkConfig.put("fs.s3a.connection.ssl.enabled", "false");
        flinkConfig.put("fs.s3a.aws.credentials.provider",
                "com.amazonaws.auth.EnvironmentVariableCredentialsProvider");
        pluginParam.set("flinkConfig", flinkConfig);
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("namePrefix", "custom-flink");
        kubernetes.put("namespace", "datafusion");
        kubernetes.put("image", "flink:2.2.0-scala_2.12-java17");
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
        kubernetes.put("serviceAccountName", "flink-runner");
        kubernetes.put("upgradeMode", "stateless");
        ObjectNode env = OBJECT_MAPPER.createObjectNode();
        env.put("HADOOP_CONF_DIR", "/opt/flink/conf");
        kubernetes.set("env", env);
        ObjectNode secretRef = OBJECT_MAPPER.createObjectNode();
        secretRef.putObject("secretRef").put("name", "flink-objectstore");
        kubernetes.set("envFrom", OBJECT_MAPPER.createArrayNode().add(secretRef));
        ObjectNode jobManager = OBJECT_MAPPER.createObjectNode();
        jobManager.put("replicas", 1);
        jobManager.putObject("resource").put("memory", "2048m").put("cpu", "1.0");
        kubernetes.set("jobManager", jobManager);
        ObjectNode taskManager = OBJECT_MAPPER.createObjectNode();
        taskManager.put("replicas", 1);
        taskManager.putObject("resource").put("memory", "4096m").put("cpu", "2.0");
        kubernetes.set("taskManager", taskManager);
        pluginParam.set("kubernetes", kubernetes);
        return pluginParam;
    }

    private ObjectNode taskData() {
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        ObjectNode job = OBJECT_MAPPER.createObjectNode();
        job.put("id", "kafka-json-job");
        taskData.set("job", job);
        ObjectNode flinkConfig = OBJECT_MAPPER.createObjectNode();
        flinkConfig.put("execution.checkpointing.dir", "file:///tmp/flink-checkpoints/kafka-json-job");
        flinkConfig.put("state.backend.type", "hashmap");
        taskData.set("flinkConfig", flinkConfig);
        ObjectNode options = OBJECT_MAPPER.createObjectNode();
        options.put("s3.endpoint", "http://sz-s3.indusmind.me");
        options.put("s3.path.style.access", "true");
        options.put("s3.secret-key", "must-not-render");
        ObjectNode sink = OBJECT_MAPPER.createObjectNode();
        sink.set("options", options);
        taskData.set("sink", sink);
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("upgradeMode", "savepoint");
        kubernetes.put("jobState", "suspended");
        taskData.set("kubernetes", kubernetes);
        return taskData;
    }

    private String resolvePluginsRootDir() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path modulePluginsDir = workingDir.resolve("src/main/resources/plugins");
        if (Files.isDirectory(modulePluginsDir)) {
            return modulePluginsDir.toString();
        }
        return workingDir.resolve("datafusion-agent/src/main/resources/plugins").toString();
    }
}
