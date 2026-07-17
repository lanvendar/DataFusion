package com.datafusion.agent.runtime.worker.plugin.spider.local;

import com.datafusion.agent.runtime.worker.plugin.shell.local.ShellLocalPluginTaskExecutor;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SpiderLocalPluginTaskExecutor}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class SpiderLocalPluginTaskExecutorTest {

    @Test
    void shouldDelegateSubmitWithSameActionContext() {
        ShellLocalPluginTaskExecutor shell = mock(ShellLocalPluginTaskExecutor.class);
        SpiderLocalPluginTaskExecutor spider = new SpiderLocalPluginTaskExecutor(shell);
        RunningTaskContext context = new RunningTaskContext(
                WorkerTaskExecutionSnap.builder().taskInstanceId("task-1").build(),
                WorkerTaskExecutionState.builder().taskInstanceId("task-1").build(),
                "/runtime/task-1");
        WorkerResult expected = new WorkerResult();
        when(shell.submit(context)).thenReturn(expected);

        WorkerResult actual = spider.submit(context);

        assertSame(expected, actual);
        verify(shell).submit(context);
    }
}
