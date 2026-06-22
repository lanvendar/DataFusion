package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DataxParamResolver}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
class DataxParamResolverTest {

    /**
     * Object mapper.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldRequirePluginParamRunMode() {
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());
        TaskRequest request = request(OBJECT_MAPPER.createObjectNode());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(request));

        assertEquals("pluginParam.runMode不能为空", exception.getMessage());
    }

    @Test
    void shouldResolveLocalRunModeFromPluginParam() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "LOCAL");
        pluginParam.put("dataxHome", "/opt/plugins/datax");
        pluginParam.put("dataxJar", "/opt/plugins/datax/lib/datax-bundle-0.0.1.jar");
        pluginParam.put("logConfigFile", "/opt/plugins/datax/conf/logback.xml");
        pluginParam.put("javaBin", "/usr/bin/java");
        pluginParam.put("logLevel", "WARN");
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        DataxExecutionParam param = resolver.resolve(request(pluginParam));

        assertEquals(DataxRunMode.LOCAL, param.getRunMode());
        assertEquals("/opt/plugins/datax", param.getDataxHome());
        assertEquals("/opt/plugins/datax/lib/datax-bundle-0.0.1.jar", param.getDataxJar());
        assertEquals("/opt/plugins/datax/conf/logback.xml", param.getLogbackConfigFile());
        assertEquals("/usr/bin/java", param.getJavaBin());
        assertEquals("WARN", param.getLogLevel());
        assertTrue(param.getWorkDir().toString().contains("task-runtime"));
    }

    @Test
    void shouldResolveKubernetesTaskOverrideFromTaskData() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode pluginEnv = OBJECT_MAPPER.createObjectNode();
        pluginEnv.put("PLUGIN_ENV", "plugin");
        pluginParam.set("env", pluginEnv);
        ObjectNode pluginKubernetes = OBJECT_MAPPER.createObjectNode();
        pluginKubernetes.put("namespace", "plugin-ns");
        pluginKubernetes.put("image", "datafusion/datax:plugin");
        pluginKubernetes.put("collectLogsOnFinish", true);
        ObjectNode pluginKubernetesEnv = OBJECT_MAPPER.createObjectNode();
        pluginKubernetesEnv.put("K8S_ENV", "plugin-k8s");
        pluginKubernetes.set("env", pluginKubernetesEnv);
        pluginParam.set("kubernetes", pluginKubernetes);
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.set("jobJson", OBJECT_MAPPER.createObjectNode());
        ObjectNode taskEnv = OBJECT_MAPPER.createObjectNode();
        taskEnv.put("TASK_ENV", "task");
        taskData.set("env", taskEnv);
        ObjectNode taskKubernetes = OBJECT_MAPPER.createObjectNode();
        taskKubernetes.put("namespace", "task-ns");
        taskKubernetes.put("image", "datafusion/datax:task");
        taskKubernetes.put("collectLogsOnFinish", false);
        ObjectNode taskKubernetesEnv = OBJECT_MAPPER.createObjectNode();
        taskKubernetesEnv.put("K8S_ENV", "task-k8s");
        taskKubernetesEnv.put("TASK_K8S_ENV", "task-k8s");
        taskKubernetes.set("env", taskKubernetesEnv);
        taskData.set("kubernetes", taskKubernetes);
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        DataxExecutionParam param = resolver.resolve(request(pluginParam, taskData));

        assertEquals(DataxRunMode.K8S, param.getRunMode());
        assertEquals("task-ns", param.getKubernetes().getNamespace());
        assertEquals("datafusion/datax:task", param.getKubernetes().getImage());
        assertFalse(param.getKubernetes().isCollectLogsOnFinish());
        assertEquals("plugin", param.getKubernetes().getEnv().get("PLUGIN_ENV"));
        assertEquals("task-k8s", param.getKubernetes().getEnv().get("K8S_ENV"));
        assertEquals("task-k8s", param.getKubernetes().getEnv().get("TASK_K8S_ENV"));
        assertFalse(param.getKubernetes().getEnv().containsKey("TASK_ENV"));
    }

    @Test
    void shouldMergeStandardJobContentAndIgnoreRegisterMetadata() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "LOCAL");
        ObjectNode defaultTaskData = OBJECT_MAPPER.createObjectNode();
        ObjectNode defaultJob = OBJECT_MAPPER.createObjectNode();
        ObjectNode setting = OBJECT_MAPPER.createObjectNode();
        ObjectNode speed = OBJECT_MAPPER.createObjectNode();
        speed.put("channel", 1);
        setting.set("speed", speed);
        defaultJob.set("setting", setting);
        defaultTaskData.set("job", defaultJob);
        pluginParam.set("defaultTaskData", defaultTaskData);
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.put("bizRef", "bizref:v1:system=DATAFUSION_PLUGIN_DATAX:bizType=DATAX_K8S_JOB:bizKey=task-1");
        ObjectNode contentItem = OBJECT_MAPPER.createObjectNode();
        contentItem.set("reader", OBJECT_MAPPER.createObjectNode().put("name", "mysqlreader"));
        contentItem.set("writer", OBJECT_MAPPER.createObjectNode().put("name", "paimonwriter"));
        ObjectNode taskJob = OBJECT_MAPPER.createObjectNode();
        taskJob.set("content", OBJECT_MAPPER.createArrayNode().add(contentItem));
        taskData.set("job", taskJob);
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        DataxExecutionParam param = resolver.resolve(request(pluginParam, taskData));

        assertFalse(param.getEffectiveTaskData().has("bizRef"));
        assertEquals(1, param.getEffectiveTaskData().path("job").path("setting").path("speed")
                .path("channel").asInt());
        assertEquals("mysqlreader", param.getEffectiveTaskData().path("job").path("content").get(0)
                .path("reader").path("name").asText());
        assertEquals("paimonwriter", param.getEffectiveTaskData().path("job").path("content").get(0)
                .path("writer").path("name").asText());
    }

    @Test
    void shouldRequireJobSource() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "LOCAL");
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(request(pluginParam, OBJECT_MAPPER.createObjectNode())));

        assertEquals("pluginParam.jobFile、pluginParam.defaultTaskData、taskData.jobJson、taskData至少配置一个",
                exception.getMessage());
    }

    @Test
    void shouldRequireKubernetesImage() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        pluginParam.set("kubernetes", OBJECT_MAPPER.createObjectNode());
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(request(pluginParam)));

        assertEquals("pluginParam.kubernetes.image或taskData.kubernetes.image不能为空", exception.getMessage());
    }

    private TaskRequest request(ObjectNode pluginParam) {
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.set("jobJson", OBJECT_MAPPER.createObjectNode());
        return request(pluginParam, taskData);
    }

    private TaskRequest request(ObjectNode pluginParam, ObjectNode taskData) {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("DataX");
        request.setPluginType(DataxPluginTaskExecutor.PLUGIN_TYPE);
        request.setPluginParam(pluginParam);
        request.setTaskData(taskData);
        return request;
    }
}
