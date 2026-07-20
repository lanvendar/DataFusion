package com.datafusion.scheduler.master.task.handler;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.datafusion.scheduler.enums.ActionType.RUN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TaskStopMsgHandler}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/16
 * @since 1.0.0
 */
class TaskStopMsgHandlerTest {

    @Test
    void shouldRejectManualStopWhileSubmitting() {
        TaskStopMsgHandler handler = new TaskStopMsgHandler(mock(TaskStorage.class), null, mock(MasterTaskOperator.class));

        assertFalse(handler.getManualPreState().contains(StatusEnum.SUBMITTING));
    }

    @Test
    void shouldPersistReportedStopFailureResult() {
        TaskInstance taskInstance = taskInstance(StatusEnum.STOPPING);
        TaskStorage taskStorage = taskStorage(taskInstance);
        TaskResult taskResult = taskResult(StatusEnum.STOP_FAILURE, "Flink stop failed");
        TaskStopMsgHandler handler = new TaskStopMsgHandler(taskStorage, null, mock(MasterTaskOperator.class));

        handler.handleAction(taskMsg(taskResult), null);

        assertEquals(StatusEnum.STOP_FAILURE, taskInstance.getState());
        assertSame(taskResult, taskInstance.getTaskResult());
        assertEquals("Flink stop failed", taskInstance.getTaskResult().getWorkerResult().getMessage());
        verify(taskStorage).saveInstance(taskInstance);
    }

    @Test
    void shouldPersistSynchronousStopResult() throws Exception {
        TaskInstance taskInstance = taskInstance(StatusEnum.RUNNING);
        TaskStorage taskStorage = taskStorage(taskInstance);
        TaskResult taskResult = taskResult(StatusEnum.STOP_SUCCESS, "Flink stopped");
        MasterTaskOperator masterTaskOperator = mock(MasterTaskOperator.class);
        when(masterTaskOperator.stopTask(taskInstance)).thenReturn(taskResult);
        TaskStopMsgHandler handler = new TaskStopMsgHandler(taskStorage, null, masterTaskOperator);

        handler.handleManualAction(taskMsg(null), null);

        assertEquals(StatusEnum.STOP_SUCCESS, taskInstance.getState());
        assertSame(taskResult, taskInstance.getTaskResult());
        assertEquals("Flink stopped", taskInstance.getTaskResult().getWorkerResult().getMessage());
    }

    @Test
    void shouldForwardRunSuccessReturnedByStopRequest() throws Exception {
        TaskInstance taskInstance = taskInstance(StatusEnum.RUNNING);
        TaskStorage taskStorage = taskStorage(taskInstance);
        TaskResult taskResult = taskResult(StatusEnum.RUN_SUCCESS, "Task already completed");
        MasterTaskOperator masterTaskOperator = mock(MasterTaskOperator.class);
        ActorSysContext context = mock(ActorSysContext.class);
        when(masterTaskOperator.stopTask(taskInstance)).thenReturn(taskResult);
        TaskStopMsgHandler handler = new TaskStopMsgHandler(taskStorage, null, masterTaskOperator);

        handler.handleManualAction(taskMsg(null), context);

        ArgumentCaptor<TaskMsg> messageCaptor = ArgumentCaptor.forClass(TaskMsg.class);
        verify(context).notify(messageCaptor.capture());
        TaskMsg forwarded = messageCaptor.getValue();
        assertEquals(RUN, forwarded.getActionType());
        assertSame(taskResult, forwarded.getTaskResult());
        assertEquals(StatusEnum.STOPPING, taskInstance.getState());
    }

    @Test
    void shouldWaitForReportWhenStopRequestReturnsStopping() throws Exception {
        TaskInstance taskInstance = taskInstance(StatusEnum.RUNNING);
        TaskStorage taskStorage = taskStorage(taskInstance);
        TaskResult taskResult = taskResult(StatusEnum.STOPPING, "Stopping");
        MasterTaskOperator masterTaskOperator = mock(MasterTaskOperator.class);
        ActorSysContext context = mock(ActorSysContext.class);
        when(masterTaskOperator.stopTask(taskInstance)).thenReturn(taskResult);
        TaskStopMsgHandler handler = new TaskStopMsgHandler(taskStorage, null, masterTaskOperator);

        handler.handleManualAction(taskMsg(null), context);

        assertEquals(StatusEnum.STOPPING, taskInstance.getState());
        verify(context, never()).notify(any(TaskMsg.class));
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

    private TaskMsg taskMsg(TaskResult taskResult) {
        return TaskMsg.builder()
                .taskInstanceId("task-1")
                .taskResult(taskResult)
                .build();
    }

    private TaskResult taskResult(StatusEnum state, String message) {
        return TaskResult.builder()
                .taskInstanceId("task-1")
                .taskState(state)
                .workerResult(WorkerResult.builder().message(message).build())
                .build();
    }
}
