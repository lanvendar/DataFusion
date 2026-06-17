package com.datafusion.agent.runtime.worker.plugin.datax;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.TaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        pluginParam.put("resourcesRoot", "/opt/plugins");
        pluginParam.put("javaBin", "/usr/bin/java");
        pluginParam.put("logLevel", "WARN");
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        DataxExecutionParam param = resolver.resolve(request(pluginParam));

        assertEquals(DataxRunMode.LOCAL, param.getRunMode());
        assertEquals("task-1.json", param.getJobName());
        assertEquals("/opt/plugins", param.getResourcesRoot());
        assertEquals("/opt/plugins/datax", param.getDataxHome());
        assertEquals("/opt/plugins/datax/lib/datax-bundle-0.0.1.jar", param.getDataxJar());
        assertEquals("/opt/plugins/datax/conf/logback.xml", param.getLogbackConfigFile());
        assertEquals("/usr/bin/java", param.getJavaBin());
        assertEquals("WARN", param.getLogLevel());
    }

    @Test
    void shouldResolveKubernetesTaskOverrideFromTaskData() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode pluginKubernetes = OBJECT_MAPPER.createObjectNode();
        pluginKubernetes.put("namespace", "plugin-ns");
        pluginKubernetes.put("image", "datafusion/datax:plugin");
        pluginKubernetes.put("collectLogsOnFinish", true);
        pluginParam.set("kubernetes", pluginKubernetes);
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.set("jobJson", OBJECT_MAPPER.createObjectNode());
        ObjectNode taskKubernetes = OBJECT_MAPPER.createObjectNode();
        taskKubernetes.put("namespace", "task-ns");
        taskKubernetes.put("image", "datafusion/datax:task");
        taskKubernetes.put("collectLogsOnFinish", false);
        taskData.set("kubernetes", taskKubernetes);
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        DataxExecutionParam param = resolver.resolve(request(pluginParam, taskData));

        assertEquals(DataxRunMode.K8S, param.getRunMode());
        assertEquals("task-ns", param.getKubernetes().getNamespace());
        assertEquals("datafusion/datax:task", param.getKubernetes().getImage());
        assertFalse(param.getKubernetes().isCollectLogsOnFinish());
    }

    @Test
    void shouldRejectLocalJobPathForKubernetesRunMode() {
        ObjectNode pluginParam = OBJECT_MAPPER.createObjectNode();
        pluginParam.put("runMode", "K8S");
        ObjectNode taskData = OBJECT_MAPPER.createObjectNode();
        taskData.put("jobPath", "/tmp/job.json");
        DataxParamResolver resolver = new DataxParamResolver(new AgentProperties());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(request(pluginParam, taskData)));

        assertEquals("taskData.jobPath仅支持LOCAL运行模式", exception.getMessage());
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
