package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spark 参数解析器测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/10
 * @since 1.0.0
 */
class SparkParamResolverTest {

    /**
     * JSON 处理器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldResolveKubernetesOverrideAndExcludeItFromEffectiveTaskData() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", SparkRunMode.K8S_OPERATOR.name());
        ObjectNode pluginKubernetes = pluginParam.putObject("kubernetes");
        pluginKubernetes.put("namePrefix", "custom-spark");
        pluginKubernetes.put("namespace", "plugin-ns");
        pluginKubernetes.put("image", "apache/spark:4.0.2-scala2.13-java17-ubuntu");
        pluginKubernetes.put("sharedPvcName", "datafusion-shared-data");
        pluginParam.putObject("defaultTaskData").put("enableSqlLogging", true);

        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.putObject("job").put("id", "spark-sql-test");
        taskData.putArray("statements").addObject().put("sql", "select 1");
        ObjectNode taskKubernetes = taskData.putObject("kubernetes");
        taskKubernetes.put("namePrefix", "ignored-task-spark");
        taskKubernetes.put("namespace", "task-ns");
        taskKubernetes.put("collectLogsOnFinish", false);

        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);

        SparkExecutionParam param = new SparkParamResolver(new AgentProperties()).resolve(request);

        assertEquals("task-ns", param.getKubernetes().getNamespace());
        assertEquals("custom-spark-task-1", param.getKubernetes().getApplicationName());
        assertEquals("custom-spark-job-config-task-1", param.getKubernetes().getConfigMapName());
        assertFalse(param.getKubernetes().isCollectLogsOnFinish());
        assertFalse(param.getEffectiveTaskData().has("kubernetes"));
        assertEquals("spark-sql-test", param.getEffectiveTaskData().path("job").path("id").asText());
        assertEquals("select 1", param.getEffectiveTaskData().path("statements").get(0).path("sql").asText());
        assertTrue(param.getEffectiveTaskData().path("enableSqlLogging").asBoolean());
    }
}
