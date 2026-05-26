package com.datafusion.scheduler.master.flow;

import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorMsg;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.actor.enums.ActorStopReason;
import com.datafusion.scheduler.master.flow.handler.FlowMsgHandler;
import com.datafusion.scheduler.master.flow.handler.FlowMsgHandlerRegister;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class FlowActorTest {

    @Test
    void shouldDispatchRunMessageWithComputedSubmittingFlowState() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowMsgHandler handler = mock(FlowMsgHandler.class);
        register.registerHandler(new DelegatingFlowMsgHandler(ActionType.RUN, handler));
        FlowActor actor = new FlowActor("flow-instance-1", register);
        ActorSysContext context = mock(ActorSysContext.class);
        actor.init(context);
        FlowMsg msg = FlowMsg.builder()
                .flowInstanceId("flow-instance-1")
                .actionType(ActionType.RUN)
                .taskState(Pair.of("task-1", StatusEnum.WAIT_DEPENDENT))
                .isManualAction(false)
                .build();

        actor.process(msg);

        ArgumentCaptor<FlowMsg> captor = ArgumentCaptor.forClass(FlowMsg.class);
        verify(handler).handle(captor.capture(), same(context));
        assertEquals(StatusEnum.SUBMITTING, captor.getValue().getFlowTargetState());
        assertEquals(StatusEnum.SUBMITTING, msg.getFlowTargetState());
    }

    @Test
    void shouldAggregateToRunningAndRunSuccessAcrossMultipleTasks() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowActor actor = new FlowActor("flow-instance-1", register);

        StatusEnum runningState = actor.updateTaskState("task-1", StatusEnum.RUNNING);
        StatusEnum successState = actor.updateTaskState("task-2", StatusEnum.RUN_SUCCESS);
        StatusEnum finalState = actor.updateTaskState("task-1", StatusEnum.RUN_SUCCESS);

        assertEquals(StatusEnum.RUNNING, runningState);
        assertEquals(StatusEnum.RUNNING, successState);
        assertEquals(StatusEnum.RUN_SUCCESS, finalState);
    }

    @Test
    void shouldAggregateToStopSuccessWhenStoppedAndSuccessfulTasksExist() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowActor actor = new FlowActor("flow-instance-1", register);

        actor.updateTaskState("task-1", StatusEnum.RUN_SUCCESS);
        StatusEnum flowState = actor.updateTaskState("task-2", StatusEnum.STOP_SUCCESS);

        assertEquals(StatusEnum.STOP_SUCCESS, flowState);
    }

    @Test
    void shouldAggregateToRunFailureWhenAnyTaskFailsInFinalStates() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowActor actor = new FlowActor("flow-instance-1", register);

        actor.updateTaskState("task-1", StatusEnum.RUN_SUCCESS);
        StatusEnum flowState = actor.updateTaskState("task-2", StatusEnum.RUN_FAILURE);

        assertEquals(StatusEnum.RUN_FAILURE, flowState);
    }

    @Test
    void shouldIgnoreNonFlowMessage() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowMsgHandler handler = mock(FlowMsgHandler.class);
        register.registerHandler(new DelegatingFlowMsgHandler(ActionType.RUN, handler));
        FlowActor actor = new FlowActor("flow-instance-1", register);

        actor.process(new DummyActorMsg());

        verify(handler, never()).handle(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void shouldClearTaskStateAfterDestroy() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowActor actor = new FlowActor("flow-instance-1", register);
        actor.updateTaskState("task-1", StatusEnum.RUNNING);

        actor.destroy(ActorStopReason.STOPPED, null);
        StatusEnum flowState = actor.updateTaskState("task-2", StatusEnum.RUN_SUCCESS);

        assertEquals(StatusEnum.RUN_SUCCESS, flowState);
    }

    @Test
    void shouldKeepFlowStateNullWhenRunMessageHasNoTaskState() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowMsgHandler handler = mock(FlowMsgHandler.class);
        register.registerHandler(new DelegatingFlowMsgHandler(ActionType.RUN, handler));
        FlowActor actor = new FlowActor("flow-instance-1", register);
        ActorSysContext context = mock(ActorSysContext.class);
        actor.init(context);
        FlowMsg msg = FlowMsg.builder()
                .flowInstanceId("flow-instance-1")
                .actionType(ActionType.RUN)
                .isManualAction(false)
                .build();

        actor.process(msg);

        ArgumentCaptor<FlowMsg> captor = ArgumentCaptor.forClass(FlowMsg.class);
        verify(handler).handle(captor.capture(), same(context));
        assertNull(captor.getValue().getFlowTargetState());
    }

    @Test
    void shouldReturnUnknownForEmptyTaskStateMap() {
        FlowMsgHandlerRegister register = new FlowMsgHandlerRegister();
        FlowActor actor = new FlowActor("flow-instance-1", register);

        StatusEnum flowState = actor.updateTaskState("task-1", StatusEnum.UNKNOWN);

        assertEquals(StatusEnum.UNKNOWN, flowState);
    }

    private static final class DelegatingFlowMsgHandler implements FlowMsgHandler {

        private final ActionType actionType;
        private final FlowMsgHandler delegate;

        private DelegatingFlowMsgHandler(ActionType actionType, FlowMsgHandler delegate) {
            this.actionType = actionType;
            this.delegate = delegate;
        }

        @Override
        public ActionType getActionType() {
            return actionType;
        }

        @Override
        public void handle(FlowMsg msg, ActorSysContext actorSysContext) {
            delegate.handle(msg, actorSysContext);
        }
    }

    private static final class DummyActorMsg implements ActorMsg {

        @Override
        public String getMsgType() {
            return "DUMMY";
        }
    }
}
