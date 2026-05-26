package com.datafusion.scheduler.master.task;

import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.task.handler.TaskMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskMsgHandlerRegister;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class TaskActorTest {

    @Test
    void shouldDispatchTaskMessageToRegisteredHandler() {
        TaskMsgHandlerRegister register = new TaskMsgHandlerRegister();
        TaskMsgHandler handler = mock(TaskMsgHandler.class);
        register.registerHandler(new DelegatingTaskMsgHandler(ActionType.SUBMIT, handler));
        TaskActor actor = new TaskActor("task-instance-1", register);
        ActorSysContext context = mock(ActorSysContext.class);
        actor.init(context);
        TaskMsg msg = TaskMsg.builder()
                .taskInstanceId("task-instance-1")
                .flowInstanceId("flow-instance-1")
                .actionType(ActionType.SUBMIT)
                .isManualAction(false)
                .build();

        actor.process(msg);

        ArgumentCaptor<TaskMsg> captor = ArgumentCaptor.forClass(TaskMsg.class);
        verify(handler).handle(captor.capture(), same(context));
        assertSame(msg, captor.getValue());
        assertEquals(ActionType.SUBMIT, captor.getValue().getActionType());
    }

    @Test
    void shouldIgnoreNonTaskMessage() {
        TaskMsgHandlerRegister register = new TaskMsgHandlerRegister();
        TaskMsgHandler handler = mock(TaskMsgHandler.class);
        register.registerHandler(new DelegatingTaskMsgHandler(ActionType.RUN, handler));
        TaskActor actor = new TaskActor("task-instance-1", register);

        actor.process(new DummyActorMsg());

        verify(handler, never()).handle(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void shouldIgnoreMessageWhenNoHandlerRegistered() {
        TaskMsgHandlerRegister register = new TaskMsgHandlerRegister();
        TaskActor actor = new TaskActor("task-instance-1", register);
        ActorSysContext context = mock(ActorSysContext.class);
        actor.init(context);
        TaskMsg msg = TaskMsg.builder()
                .taskInstanceId("task-instance-1")
                .actionType(ActionType.KILL)
                .isManualAction(true)
                .build();

        actor.process(msg);
    }

    private static final class DelegatingTaskMsgHandler implements TaskMsgHandler {

        private final ActionType actionType;
        private final TaskMsgHandler delegate;

        private DelegatingTaskMsgHandler(ActionType actionType, TaskMsgHandler delegate) {
            this.actionType = actionType;
            this.delegate = delegate;
        }

        @Override
        public ActionType getActionType() {
            return actionType;
        }

        @Override
        public void handle(TaskMsg msg, ActorSysContext context) {
            delegate.handle(msg, context);
        }
    }

    private static final class DummyActorMsg implements ActorMsg {

        @Override
        public String getMsgType() {
            return "DUMMY";
        }
    }
}
