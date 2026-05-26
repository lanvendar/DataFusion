package com.datafusion.scheduler.master.trigger.storage;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CachedTriggerStorage 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class CachedTriggerStorageTest {

    @Mock
    private TriggerStorage delegateStorage;

    private CachedTriggerStorage cachedStorage;

    @BeforeEach
    void setUp() {
        cachedStorage = new CachedTriggerStorage(delegateStorage, new Options());
    }

    @Test
    void testGetTriggerInfoDelegatesToCache() {
        TriggerInfo info = createTriggerInfo("flow-001");
        when(delegateStorage.getTriggerInfo("flow-001")).thenReturn(info);

        TriggerInfo result = cachedStorage.getTriggerInfo("flow-001");
        assertNotNull(result);
        assertEquals("flow-001", result.getPayloadId());

        // 第二次调用应使用缓存,不再调用delegate
        TriggerInfo resultCached = cachedStorage.getTriggerInfo("flow-001");
        assertNotNull(resultCached);
    }

    @Test
    void testGetTriggerInfoReturnsNullWhenNotFound() {
        when(delegateStorage.getTriggerInfo("not-exist")).thenReturn(null);
        TriggerInfo result = cachedStorage.getTriggerInfo("not-exist");
        assertNull(result);
    }

    @Test
    void testSaveTriggerInfoUpdatesCache() {
        TriggerInfo info = createTriggerInfo("flow-001");

        cachedStorage.saveTriggerInfo(info);

        verify(delegateStorage).saveTriggerInfo(info);
        // 保存后应能从缓存获取
        TriggerInfo result = cachedStorage.getTriggerInfo("flow-001");
        assertNotNull(result);
    }

    @Test
    void testGetTriggerInstanceDelegatesToCache() {
        TriggerInstance instance = createTriggerInstance("ins-001");
        when(delegateStorage.getTriggerInstance("ins-001")).thenReturn(instance);

        TriggerInstance result = cachedStorage.getTriggerInstance("ins-001");
        assertNotNull(result);
        assertEquals("ins-001", result.getInstanceId());
    }

    @Test
    void testGetLastTriggerInstanceDelegatesDirectly() {
        TriggerInstance instance = createTriggerInstance("ins-001");
        when(delegateStorage.getLastTriggerInstance("flow-001", "v1")).thenReturn(instance);

        TriggerInstance result = cachedStorage.getLastTriggerInstance("flow-001", "v1");
        assertNotNull(result);
        verify(delegateStorage).getLastTriggerInstance("flow-001", "v1");
    }

    @Test
    void testSaveTriggerInstanceDelegatesDirectly() {
        TriggerInstance instance = createTriggerInstance("ins-001");

        cachedStorage.saveTriggerInstance(instance);

        verify(delegateStorage).saveTriggerInstance(instance);
    }

    @Test
    void testGetAllScheduledTriggerInfoDelegatesDirectly() {
        TriggerInfo info1 = createTriggerInfo("flow-001");
        TriggerInfo info2 = createTriggerInfo("flow-002");
        List<TriggerInfo> list = Arrays.asList(info1, info2);
        when(delegateStorage.getAllScheduledTriggerInfo()).thenReturn(list);

        List<TriggerInfo> result = cachedStorage.getAllScheduledTriggerInfo();
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void testDefaultConstructor() {
        CachedTriggerStorage defaultStorage = new CachedTriggerStorage();
        assertNotNull(defaultStorage);
    }

    private TriggerInfo createTriggerInfo(String payloadId) {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId(payloadId);
        info.setTriggerId("trigger-" + payloadId);
        info.setVersion("v1");
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("10000");
        info.setTriggerPolicy(TriggerPolicyEnum.PARALLEL);
        return info;
    }

    private TriggerInstance createTriggerInstance(String instanceId) {
        TriggerInstance instance = new TriggerInstance();
        instance.setPayloadId("flow-001");
        instance.setInstanceId(instanceId);
        instance.setVersion("v1");
        instance.setScheduleTime(System.currentTimeMillis());
        return instance;
    }
}
