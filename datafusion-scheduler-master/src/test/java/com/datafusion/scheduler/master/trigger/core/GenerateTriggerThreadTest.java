package com.datafusion.scheduler.master.trigger.core;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.trigger.SchedulerTrigger;
import com.datafusion.scheduler.master.trigger.TriggerInstanceHandler;
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

import static org.mockito.Mockito.*;

/**
 * GenerateTriggerThread 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class GenerateTriggerThreadTest {

    @Mock
    private TriggerInstanceHandler handler;

    @Mock
    private SchedulerTrigger schedulerTrigger;

    private GenerateTriggerThread generateThread;

    private ThreadPoolExecutor executor;

    @BeforeEach
    void setUp() {
        when(handler.getSchedulerTrigger()).thenReturn(schedulerTrigger);
        executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        generateThread = new GenerateTriggerThread(executor, handler);
    }

    @Test
    void testFetchInstancesDelegatesToHandler() {
        when(handler.fetchCache()).thenReturn(Collections.emptyList());
        generateThread.fetchInstances();
        verify(handler).fetchCache();
    }

    @Test
    void testTriggerActionCallsFetchInitWhenAvailable() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(true);

        generateThread.triggerAction(instance);

        verify(schedulerTrigger).fetchInit(instance);
    }

    @Test
    void testTriggerActionSkipsWhenNotAvailable() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.checkScheduleAvailable(instance)).thenReturn(false);

        generateThread.triggerAction(instance);

        verify(schedulerTrigger, never()).fetchInit(instance);
    }

    @Test
    void testActionSuccessEnqueuesOnTrue() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        instance.setScheduleTime(1000L);

        generateThread.actionSuccess(true, instance);

        verify(handler).enqueue(instance);
    }

    @Test
    void testActionSuccessDoesNothingOnFalse() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");

        generateThread.actionSuccess(false, instance);

        verify(handler, never()).enqueue(instance);
    }

    @Test
    void testActionFailureWithExistingState() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.getScheduleInstanceState("ins-001")).thenReturn(StatusEnum.INIT_SUCCESS);

        generateThread.actionFailure(instance);

        verify(handler).saveTriggerInstance(instance);
    }

    @Test
    void testActionFailureWithNoState() {
        TriggerInstance instance = createInstance("flow-001", "ins-001", "v1");
        when(handler.getScheduleInstanceState("ins-001")).thenReturn(null);

        generateThread.actionFailure(instance);

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
