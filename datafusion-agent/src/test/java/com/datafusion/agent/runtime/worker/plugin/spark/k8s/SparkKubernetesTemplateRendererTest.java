package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;
import com.datafusion.agent.runtime.worker.plugin.spark.SparkParamResolver;
import com.datafusion.agent.runtime.worker.plugin.template.TemplateSpecRenderer;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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

        assertTrue(yaml.contains("kind: SparkApplication"));
        assertTrue(yaml.contains("kind: ConfigMap"));
        assertTrue(yaml.contains("type: Never"));
        assertTrue(yaml.contains("df-spark-task-1"));
        assertTrue(yaml.contains("df-spark-sql-job-task-1"));
        assertTrue(yaml.contains("--job-file"));
        assertTrue(yaml.contains("/opt/datafusion/spark/jobs/spark-sql-job.json"));
        assertTrue(yaml.contains("select 1"));
        assertTrue(yaml.contains("claimName: \"datafusion-shared-data\""));
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
        kubernetes.put("sharedPvcName", "datafusion-shared-data");
        kubernetes.put("pluginAppDir", "/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql");
        pluginParam.set("kubernetes", kubernetes);
        return pluginParam;
    }

    private ObjectNode taskData() {
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        ObjectNode sql = OBJECT_MAPPER.createObjectNode();
        sql.put("text", "select 1");
        taskData.set("sql", sql);
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
