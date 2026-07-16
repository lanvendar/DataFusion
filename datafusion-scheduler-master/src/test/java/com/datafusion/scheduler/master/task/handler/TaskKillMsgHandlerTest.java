package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.TaskResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TaskKillMsgHandler}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/16
 * @since 1.0.0
 */
class TaskKillMsgHandlerTest {

    @Test
    void shouldKeepKillingWhenUnknownTaskStartsAsynchronousKill() throws Exception {
        TaskInstance taskInstance = taskInstance(StatusEnum.UNKNOWN);
        TaskStorage taskStorage = taskStorage(taskInstance);
        TaskResult taskResult = taskResult(StatusEnum.KILLING);
        MasterTaskOperator masterTaskOperator = mock(MasterTaskOperator.class);
        when(masterTaskOperator.killTask(taskInstance)).thenReturn(taskResult);
        TaskKillMsgHandler handler = new TaskKillMsgHandler(taskStorage, null, masterTaskOperator);

        handler.handleManualAction(taskMsg(), null);

        assertEquals(StatusEnum.KILLING, taskInstance.getState());
        assertSame(taskResult, taskInstance.getTaskResult());
        verify(taskStorage, times(2)).saveInstance(taskInstance);
    }

    private TaskStorage taskStorage(TaskInstance taskInstance) {
        TaskStorage taskStorage = mock(TaskStorage.class);
        when(taskStorage.getInstanceById(taskInstance.getInstanceId())).thenReturn(taskInstance);
        return taskStorage;
    }

    private TaskInstance taskInstance(StatusEnum state) {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setInstanceId("task-1");
        taskInstance.setFlowInstanceId("flow-1");
        taskInstance.setState(state);
        return taskInstance;
    }

    private TaskMsg taskMsg() {
        return TaskMsg.builder()
                .taskInstanceId("task-1")
                .build();
    }

    private TaskResult taskResult(StatusEnum state) {
        return TaskResult.builder()
                .taskInstanceId("task-1")
                .taskState(state)
                .build();
    }
}
