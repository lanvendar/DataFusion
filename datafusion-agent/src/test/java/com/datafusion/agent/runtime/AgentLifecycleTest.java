package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.rpc.ManagerClient;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateReportScheduler;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AgentLifecycle}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/29
 * @since 1.0.0
 */
class AgentLifecycleTest {

    @Test
    void shouldUseLoadedPluginTypesWhenWorkerPluginTypesNotConfigured() throws Exception {
        AgentRuntimeState runtimeState = initWorker(null);

        assertEquals(List.of("SHELL", "DATAX"), runtimeState.getWorker().getPluginTypes());
    }

    @Test
    void shouldFilterWorkerPluginTypesByConfiguration() throws Exception {
        AgentRuntimeState runtimeState = initWorker("DATAX,UNKNOWN,DATAX");

        assertEquals(List.of("DATAX"), runtimeState.getWorker().getPluginTypes());
    }

    private AgentRuntimeState initWorker(String pluginTypes) throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.getWorker().setWorkerCode("worker-1");
        properties.getWorker().setIp("127.0.0.1");
        properties.getWorker().setHostName("localhost");
        properties.getWorker().setPluginTypes(pluginTypes);
        AgentRuntimeState runtimeState = new AgentRuntimeState();
        AgentWorkerConfigStore workerConfigStore = mock(AgentWorkerConfigStore.class);
        when(workerConfigStore.load()).thenReturn(null);
        AgentLifecycle lifecycle = new AgentLifecycle(properties, mock(ManagerClient.class), runtimeState,
                workerTaskOperatorRouter(), mock(ScheduledExecutorService.class),
                mock(AgentTaskStateReportScheduler.class), workerConfigStore);
        Method initWorker = AgentLifecycle.class.getDeclaredMethod("initWorker");
        initWorker.setAccessible(true);
        initWorker.invoke(lifecycle);
        return runtimeState;
    }

    private WorkerTaskOperatorRouter workerTaskOperatorRouter() {
        return new WorkerTaskOperatorRouter(List.of(new TestPluginTaskExecutor("SHELL"),
                new TestPluginTaskExecutor("DATAX")));
    }

    /**
     * Test plugin task executor.
     */
    private static class TestPluginTaskExecutor implements PluginTaskExecutor {

        /**
         * Plugin type.
         */
        private final String pluginType;

        TestPluginTaskExecutor(String pluginType) {
            this.pluginType = pluginType;
        }

        @Override
        public String pluginType() {
            return pluginType;
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public TaskResult submitTask(TaskRequest request) {
            return taskResult(request, StatusEnum.SUBMIT_SUCCESS);
        }

        @Override
        public TaskResult stopTask(TaskRequest request) {
            return taskResult(request, StatusEnum.STOP_SUCCESS);
        }

        @Override
        public TaskResult killTask(TaskRequest request) {
            return taskResult(request, StatusEnum.KILLED);
        }

        @Override
        public boolean finishTask(TaskRequest request) {
            return true;
        }

        private TaskResult taskResult(TaskRequest request, StatusEnum state) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(state)
                    .build();
        }
    }
}
