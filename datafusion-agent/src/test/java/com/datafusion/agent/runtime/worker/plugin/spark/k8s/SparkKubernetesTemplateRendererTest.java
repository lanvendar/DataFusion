package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spark Kubernetes 模板渲染测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
class SparkKubernetesTemplateRendererTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRenderSparkApplicationWithRestartPolicyNever() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setPluginsRootDir(resolvePluginsRootDir());
        SparkExecutionParam param = new SparkParamResolver(properties).resolve(request());

        SparkKubernetesTemplateRenderer renderer = new SparkKubernetesTemplateRenderer(
                new TemplateSpecRenderer(properties));
        String yaml = renderer.render(param, OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(param.getEffectiveTaskData()));

        List<Object> documents = new ArrayList<>();
        new Yaml().loadAll(yaml).forEach(documents::add);
        assertEquals(2, documents.size());
        assertTrue(yaml.contains("kind: SparkApplication"));
        assertTrue(yaml.contains("kind: ConfigMap"));
        assertTrue(yaml.contains("type: Never"));
        assertTrue(yaml.contains("df-spark-task-1"));
        assertTrue(yaml.contains("df-spark-job-config-task-1"));
        assertTrue(yaml.contains("--job-file"));
        assertTrue(yaml.contains("/opt/datafusion/spark/jobs/spark-sql-job.json"));
        assertTrue(yaml.contains("\"spark-sql-job.json\": |-"));
        assertTrue(yaml.contains("select 1"));
        assertTrue(yaml.contains("claimName: \"datafusion-shared-data\""));

        Map<?, ?> application = (Map<?, ?>) documents.get(1);
        Map<?, ?> spec = (Map<?, ?>) application.get("spec");
        assertFalse(spec.containsKey("volumes"));
        assertFalse(spec.containsKey("serviceAccount"));
        Map<?, ?> driver = (Map<?, ?>) spec.get("driver");
        assertEquals("spark", driver.get("serviceAccount"));
        assertPodTemplate(driver, "spark-kubernetes-driver", 3, 2);
        assertPodTemplate((Map<?, ?>) spec.get("executor"), "spark-kubernetes-executor", 2, 1);
    }

    private void assertPodTemplate(Map<?, ?> role, String containerName, int volumeCount, int mountCount) {
        assertFalse(role.containsKey("volumeMounts"));
        assertFalse(role.containsKey("initContainers"));

        Map<?, ?> template = (Map<?, ?>) role.get("template");
        Map<?, ?> podSpec = (Map<?, ?>) template.get("spec");
        assertEquals(volumeCount, ((List<?>) podSpec.get("volumes")).size());
        List<?> initContainers = (List<?>) podSpec.get("initContainers");
        assertEquals(1, initContainers.size());
        assertTrue(hasVolumeMount((Map<?, ?>) initContainers.get(0), "shared-plugins"));

        List<?> containers = (List<?>) podSpec.get("containers");
        assertEquals(1, containers.size());
        Map<?, ?> container = (Map<?, ?>) containers.get(0);
        assertEquals(containerName, container.get("name"));
        assertEquals(mountCount, ((List<?>) container.get("volumeMounts")).size());
        assertFalse(hasVolumeMount(container, "shared-plugins"));
    }

    private boolean hasVolumeMount(Map<?, ?> container, String volumeName) {
        List<?> volumeMounts = (List<?>) container.get("volumeMounts");
        return volumeMounts.stream().anyMatch(
                mount -> mount instanceof Map<?, ?> mountMap && volumeName.equals(mountMap.get("name")));
    }

    private TaskRequest request() {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("Spark");
        request.setPluginType("SPARK");
        request.setPluginParam(pluginParam());
        request.setTaskData(taskData());
        return request;
    }

    private ObjectNode pluginParam() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S_OPERATOR");
        ObjectNode kubernetes = OBJECT_MAPPER.createObjectNode();
        kubernetes.put("namespace", "datafusion");
        kubernetes.put("serviceAccountName", "spark");
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
        kubernetes.put("pluginAppDir", "/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql");
        pluginParam.set("kubernetes", kubernetes);
        return pluginParam;
    }

    private ObjectNode taskData() {
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.putObject("job").put("id", "spark-test");
        taskData.put("sqlTargetType", "PAIMON");
        taskData.put("catalogName", "paimon");
        taskData.put("databaseName", "ods");
        taskData.put("useDatabase", false);
        taskData.putArray("statements").addObject().put("sql", "select 1");
        ObjectNode paimonConf = taskData.putObject("paimonConf");
        paimonConf.put("spark.sql.extensions", "org.apache.paimon.spark.extensions.PaimonSparkSessionExtensions");
        paimonConf.put("spark.sql.catalog.paimon", "org.apache.paimon.spark.SparkCatalog");
        paimonConf.put("spark.sql.catalog.paimon.warehouse", "file:/tmp/paimon");
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
