package com.datafusion.scheduler.master.trigger.core;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.trigger.SchedulerTrigger;
import com.datafusion.scheduler.master.trigger.TriggerInstanceHandler;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DispatchTriggerThread 单元测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/20
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DispatchTriggerThreadTest {

    /**
     * 调度实例处理器.
     */
    @Mock
    private TriggerInstanceHandler handler;

    /**
     * 调度动作接口.
     */
    @Mock
    private SchedulerTrigger schedulerTrigger;

    /**
     * Dispatch 线程.
     */
    private DispatchTriggerThread dispatchThread;

    /**
     * 测试线程池.
     */
    private ThreadPoolExecutor executor;

    @BeforeEach
    void setUp() {
        when(handler.getSchedulerTrigger()).thenReturn(schedulerTrigger);
        executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        dispatchThread = new DispatchTriggerThread(executor, handler);
    }

    @Test
    void testFetchInstancesDelegatesToHandler() {
        when(handler.dequeue()).thenReturn(Collections.emptyList());
        dispatchThread.fetchInstances();
        verify(handler).dequeue();
    }

    @Test
    void testTriggerActionDispatchesWhenAvailableAndInitSuccess() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(true);
        when(handler.getScheduleInstanceState("ins-001")).thenReturn(StatusEnum.INIT_SUCCESS);

        Boolean result = dispatchThread.triggerAction(instance);

        assertTrue(result);
        verify(schedulerTrigger).dispatchSubmit(instance);
    }

    @Test
    void testTriggerActionReturnsFalseWhenNotAvailable() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(false);

        Boolean result = dispatchThread.triggerAction(instance);

        assertFalse(result);
        verify(schedulerTrigger, never()).cleanInitializationInstance("ins-001", "flow-001", "v1");
        verify(schedulerTrigger, never()).dispatchSubmit(instance);
    }

    @Test
    void testActionSuccessKeepsInitializationWhenScheduleResumesBeforeCleanup() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(true);

        dispatchThread.actionSuccess(false, instance);

        verify(schedulerTrigger, never()).cleanInitializationInstance("ins-001", "flow-001", "v1");
        verify(handler, never()).saveLastScheduleInstance(instance);
    }

    @Test
    void testTriggerActionReturnsFalseWhenStateNotInitSuccess() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(true);
        when(handler.getScheduleInstanceState("ins-001")).thenReturn(StatusEnum.INIT_FAILURE);

        Boolean result = dispatchThread.triggerAction(instance);

        assertFalse(result);
        verify(schedulerTrigger, never()).dispatchSubmit(instance);
    }

    @Test
    void testActionSuccessSavesLastInstanceAndGeneratesNext() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        instance.setScheduleTime(1000L);

        when(handler.checkScheduleAvailable(instance)).thenReturn(true);

        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-001");
        info.setVersion("v1");
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("10000");
        info.setStartTime(0L);
        info.setEndTime(Long.MAX_VALUE);
        when(handler.getTriggerInfo("flow-001")).thenReturn(info);

        dispatchThread.actionSuccess(true, instance);

        verify(handler).saveLastScheduleInstance(instance);
        verify(handler).generateInstance(info, 1000L, false);
    }

    @Test
    void testActionSuccessCleansInitializationWhenScheduleRemainsUnavailable() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(false);

        dispatchThread.actionSuccess(false, instance);

        verify(schedulerTrigger).cleanInitializationInstance("ins-001", "flow-001", "v1");
        verify(handler, never()).saveLastScheduleInstance(instance);
    }

    @Test
    void testActionFailureAddsCacheBack() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");

        dispatchThread.actionFailure(instance);

        verify(handler).addCache(instance);
    }

    private TriggerInstance createInstance(String payloadId, String instanceId, String version) {
        TriggerInstance instance = new TriggerInstance();
        instance.setPayloadId(payloadId);
        instance.setInstanceId(instanceId);
        instance.setVersion(version);
        return instance;
    }
}
