package com.datafusion.agent.runtime.worker.plugin.spark;

import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
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
        ObjectNode pluginKubernetes = pluginParam.putObject("kubernetes");
        pluginKubernetes.put("namePrefix", "custom-spark");
        pluginKubernetes.put("namespace", "plugin-ns");
        pluginKubernetes.put("image", "apache/spark:4.0.2-scala2.13-java17-ubuntu");
        pluginKubernetes.put("serviceAccountName", "spark-driver");
        pluginKubernetes.put("sharedPvcName", "datafusion-shared-data");
        ObjectNode defaultTaskData = pluginParam.putObject("defaultTaskData");
        defaultTaskData.put("enableSqlLogging", true);

        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.putObject("job").put("id", "spark-sql-test");
        taskData.putArray("statements").addObject().put("sql", "select 1");
        ObjectNode taskKubernetes = taskData.putObject("kubernetes");
        taskKubernetes.put("namePrefix", "ignored-task-spark");
        taskKubernetes.put("namespace", "task-ns");
        taskKubernetes.put("collectLogsOnFinish", false);

        WorkerTaskExecutionSnap snapshot = WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .runMode(SparkRunMode.K8S_OPERATOR.name())
                .pluginParam(pluginParam)
                .taskData(taskData)
                .build();

        SparkExecutionParam param = new SparkParamResolver().resolve(snapshot, "/tmp/datafusion/spark/task-1");

        assertEquals("/tmp/datafusion/spark/task-1", param.getWorkDir().toString());
        assertEquals("task-ns", param.getKubernetes().getNamespace());
        assertEquals("custom-spark-task-1", param.getKubernetes().getApplicationName());
        assertEquals("custom-spark-job-config-task-1", param.getKubernetes().getConfigMapName());
        assertEquals("spark-driver", param.getKubernetes().getServiceAccountName());
        assertEquals("/opt/datafusion", param.getKubernetes().getSharedMountPath());
        assertEquals("/opt/spark/work-dir/datafusion-jars", param.getKubernetes().getJarMountPath());
        assertFalse(param.getKubernetes().isCollectLogsOnFinish());
        assertFalse(param.getEffectiveTaskData().has("kubernetes"));
        assertEquals("spark-sql-test", param.getEffectiveTaskData().path("job").path("id").asText());
        assertEquals("select 1", param.getEffectiveTaskData().path("statements").get(0).path("sql").asText());
        assertTrue(param.getEffectiveTaskData().path("enableSqlLogging").asBoolean());
    }
}
