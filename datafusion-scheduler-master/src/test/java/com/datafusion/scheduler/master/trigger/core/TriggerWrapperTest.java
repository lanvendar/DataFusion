package com.datafusion.scheduler.master.trigger.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TriggerWrapper 单元测试.
 */
class TriggerWrapperTest {

    @Test
    void testEqualsWithSameTriggerTimeAndPayload() {
        TriggerWrapper<String> a = createWrapper("payload-1", 1000L);
        TriggerWrapper<String> b = createWrapper("payload-1", 1000L);
        assertEquals(a, b);
    }

    @Test
    void testNotEqualsWithDifferentPayload() {
        TriggerWrapper<String> a = createWrapper("payload-1", 1000L);
        TriggerWrapper<String> b = createWrapper("payload-2", 1000L);
        assertNotEquals(a, b);
    }

    @Test
    void testNotEqualsWithDifferentTriggerTime() {
        TriggerWrapper<String> a = createWrapper("payload-1", 1000L);
        TriggerWrapper<String> b = createWrapper("payload-1", 2000L);
        assertNotEquals(a, b);
    }

    @Test
    void testHashCodeConsistency() {
        TriggerWrapper<String> a = createWrapper("payload-1", 1000L);
        TriggerWrapper<String> b = createWrapper("payload-1", 1000L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testCancelledDefault() {
        TriggerWrapper<String> wrapper = createWrapper("payload-1", 1000L);
        assertFalse(wrapper.isCancelled());
    }

    @Test
    void testSetCancelled() {
        TriggerWrapper<String> wrapper = createWrapper("payload-1", 1000L);
        wrapper.setCancelled(true);
        assertTrue(wrapper.isCancelled());
    }

    private TriggerWrapper<String> createWrapper(String payload, long triggerTime) {
        TriggerWrapper<String> wrapper = new TriggerWrapper<>();
        wrapper.setPayload(payload);
        wrapper.setTriggerTime(triggerTime);
        return wrapper;
    }
}