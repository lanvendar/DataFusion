package com.datafusion.agent.runtime;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.WorkerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AgentLifecycle}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class AgentLifecycleTest {

    @Test
    void shouldCreateConfiguredWorkerAndStartService() throws Exception {
        AgentProperties properties = properties();
        WorkerService workerService = mock(WorkerService.class);
        when(workerService.pluginTypes()).thenReturn(Set.of("DATAX", "SHELL"));
        AgentLifecycle lifecycle = new AgentLifecycle(properties, workerService);

        lifecycle.run(new DefaultApplicationArguments());

        ArgumentCaptor<Worker> captor = ArgumentCaptor.forClass(Worker.class);
        verify(workerService).start(captor.capture());
        Worker worker = captor.getValue();
        assertEquals("worker-code", worker.getWorkerCode());
        assertEquals("127.0.0.2", worker.getIp());
        assertEquals("agent-host", worker.getHostName());
        assertEquals(18081, worker.getPort());
        assertEquals(java.util.List.of("DATAX"), worker.getPluginTypes());
    }

    @Test
    void shouldStopWorkerServiceOnDestroy() {
        WorkerService workerService = mock(WorkerService.class);
        AgentLifecycle lifecycle = new AgentLifecycle(properties(), workerService);

        lifecycle.destroy();

        verify(workerService).stop();
    }

    private AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.getWorker().setWorkerCode("worker-code");
        properties.getWorker().setIp("127.0.0.2");
        properties.getWorker().setHostName("agent-host");
        properties.getWorker().setPort(18081);
        properties.getWorker().setPluginTypes("DATAX,UNKNOWN,DATAX");
        return properties;
    }
}
